package com.example.authsvc.api.controller;

import com.example.authsvc.api.dto.request.MfaEnrollConfirmRequest;
import com.example.authsvc.api.dto.request.MfaVerifyLoginRequest;
import com.example.authsvc.api.dto.response.LoginResponse;
import com.example.authsvc.api.dto.response.LoginResult;
import com.example.authsvc.api.dto.response.MfaEnrollConfirmResponse;
import com.example.authsvc.api.dto.response.MfaEnrollResponse;
import com.example.authsvc.api.dto.response.MfaVerifyLoginResponse;
import com.example.authsvc.application.service.LoginExecutionService;
import com.example.authsvc.common.exception.UnauthorizedException;
import com.example.authsvc.domain.model.MfaChallengeEntry;
import com.example.authsvc.domain.model.MfaEnrollment;
import com.example.authsvc.domain.port.MfaChallengeStore;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserJpaRepository;
import com.example.authsvc.infrastructure.security.cookie.AuthCookieFactory;
import com.example.authsvc.infrastructure.security.principal.AuthenticatedUser;
import com.example.authsvc.application.service.MfaService;
import com.example.authsvc.config.properties.AuthBehaviorProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * MFA enrollment/confirmation/disable and challenge-based login completion.
 * Only registered when {@code app.mfa.enabled=true}.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth/mfa")
@ConditionalOnProperty(prefix = "app.mfa", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class MfaController {

    private static final int MAX_CHALLENGE_ATTEMPTS = 5;

    private final MfaService mfaService;
    private final MfaChallengeStore challengeStore;
    private final AuthUserJpaRepository userRepo;
    private final LoginExecutionService loginExecutor;
    private final AuthCookieFactory cookieFactory;
    private final AuthBehaviorProperties behaviorProps;

    @PostMapping("/enroll")
    public ResponseEntity<MfaEnrollResponse> enroll(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(value = "challengeToken", required = false) String challengeToken) {
        UUID userId;
        if (principal != null) {
            userId = principal.getUserId();
        } else if (challengeToken != null) {
            MfaChallengeEntry entry = challengeStore.find(challengeToken).orElseThrow(UnauthorizedException::new);
            if (!entry.enrollmentRequired()) {
                // A normal (already-enrolled) login challenge token is only valid
                // for /mfa/verify-login — it must never be usable to generate a
                // NEW secret without the caller having proved control of the
                // existing enrolled device.
                throw new UnauthorizedException();
            }
            userId = entry.userId();
        } else {
            throw new UnauthorizedException();
        }
        // AuthenticatedUser is built from JWT claims and carries no email field
        // (see JwtClaims: userId/tenantId/tenantSlug/roleIds/userType/sessionId/
        // expiresAt/jti only) — look it up from the user record instead.
        AuthUserEntity user = userRepo.findById(userId).orElseThrow(UnauthorizedException::new);
        MfaEnrollment enrollment = mfaService.enroll(userId, user.getEmail());
        return ResponseEntity.ok(new MfaEnrollResponse(enrollment.otpauthUri(), enrollment.base32Secret()));
    }

    @PostMapping("/enroll/confirm")
    public ResponseEntity<MfaEnrollConfirmResponse> confirmEnrollment(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody MfaEnrollConfirmRequest request) {
        List<String> backupCodes = mfaService.confirmEnrollment(principal.getUserId(), request.code());
        return ResponseEntity.ok(new MfaEnrollConfirmResponse(backupCodes));
    }

    @PostMapping("/disable")
    public ResponseEntity<Void> disable(@AuthenticationPrincipal AuthenticatedUser principal) {
        mfaService.disable(principal.getUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-login")
    public ResponseEntity<?> verifyLogin(@Valid @RequestBody MfaVerifyLoginRequest request,
                                         HttpServletRequest httpRequest) {
        MfaChallengeEntry entry = challengeStore.find(request.challengeToken())
                .orElseThrow(UnauthorizedException::new);

        if (entry.attempts() >= MAX_CHALLENGE_ATTEMPTS) {
            challengeStore.delete(request.challengeToken());
            throw new UnauthorizedException();
        }

        List<String> backupCodes = null;
        if (entry.enrollmentRequired() && !mfaService.isEnrolled(entry.userId())) {
            // First code from an unenrolled-but-required user both confirms
            // enrollment and completes login in the same call. This is the
            // only path where confirmEnrollment(...)'s backup codes are ever
            // generated without a separate /mfa/enroll/confirm call to
            // surface them, so capture them for the response below.
            try {
                backupCodes = mfaService.confirmEnrollment(entry.userId(), request.code());
            } catch (UnauthorizedException e) {
                challengeStore.incrementAttempts(request.challengeToken());
                throw e;
            }
        } else if (!mfaService.verifyCode(entry.userId(), request.code())) {
            challengeStore.incrementAttempts(request.challengeToken());
            throw new UnauthorizedException();
        }

        challengeStore.delete(request.challengeToken());

        AuthUserEntity user = userRepo.findById(entry.userId()).orElseThrow(UnauthorizedException::new);
        String ip = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");

        LoginResult result = loginExecutor.issueTokens(user, buildSyntheticLoginRequest(user),
                ip, userAgent, System.currentTimeMillis());

        log.info("mfa.login.completed userId={}", entry.userId());

        // Mirror AuthController.login()'s cookie-vs-JSON branching exactly —
        // this endpoint completes a login just like /login does, and must
        // honor the same auth.token-delivery-mode contract (cookie-mode
        // deployments need the Set-Cookie headers, not just the response body).
        if (behaviorProps.isJsonTokenDelivery()) {
            if (backupCodes != null) {
                MfaVerifyLoginResponse body = new MfaVerifyLoginResponse(
                        result.response().getUserId(),
                        result.response().getEmail(),
                        result.response().getAccessTokenExpiresAt(),
                        result.accessToken(),
                        result.refreshToken(),
                        backupCodes);
                return ResponseEntity.ok(body);
            }
            LoginResponse body = new LoginResponse(
                    result.response().getUserId(),
                    result.response().getEmail(),
                    result.response().getAccessTokenExpiresAt(),
                    result.accessToken(),
                    result.refreshToken());
            return ResponseEntity.ok(body);
        }

        ResponseCookie accessCookie = cookieFactory.createAccessTokenCookie(
                result.accessToken(), result.accessTokenTtl());
        ResponseCookie refreshCookie = cookieFactory.createRefreshTokenCookie(
                result.refreshToken(), result.refreshTokenTtl());

        // backupCodes != null only on the enrollment-completing path; LoginResponse
        // itself must never gain a backupCodes field since /login and /refresh
        // also return it and never carry codes.
        Object cookieModeBody = backupCodes != null
                ? new MfaVerifyLoginResponse(
                        result.response().getUserId(),
                        result.response().getEmail(),
                        result.response().getAccessTokenExpiresAt(),
                        result.response().getAccessToken(),
                        null,
                        backupCodes)
                : result.response();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clearLegacyRefreshTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clearOldNarrowRefreshTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(cookieModeBody);
    }

    private com.example.authsvc.api.dto.request.LoginRequest buildSyntheticLoginRequest(AuthUserEntity user) {
        // issueTokens(...) reads request.getEmail() nowhere in its body (only
        // executeLogin's password-verification block did) — a bare LoginRequest
        // carrying just the email keeps the method signature unchanged without
        // requiring a second overload.
        com.example.authsvc.api.dto.request.LoginRequest synthetic = new com.example.authsvc.api.dto.request.LoginRequest();
        synthetic.setEmail(user.getEmail());
        return synthetic;
    }
}
