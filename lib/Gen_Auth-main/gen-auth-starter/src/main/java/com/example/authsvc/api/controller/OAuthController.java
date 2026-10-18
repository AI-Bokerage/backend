package com.example.authsvc.api.controller;

import com.example.authsvc.api.dto.request.OAuthCompleteSignupRequest;
import com.example.authsvc.api.dto.response.LoginResult;
import com.example.authsvc.api.dto.response.MfaLoginChallengeResponse;
import com.example.authsvc.application.service.LoginExecutionService;
import com.example.authsvc.common.exception.UnauthorizedException;
import com.example.authsvc.config.properties.AuthBehaviorProperties;
import com.example.authsvc.domain.model.OAuthSignupChallengeEntry;
import com.example.authsvc.domain.port.OAuthSignupChallengeStore;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserJpaRepository;
import com.example.authsvc.infrastructure.security.cookie.AuthCookieFactory;
import com.example.authsvc.infrastructure.security.password.PasswordHasher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Completes an OAuth-initiated signup that's been blocked pending a password
 * (see {@code OAuthLoginSuccessHandler}). Only registered when
 * {@code app.oauth.enabled=true}.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth/oauth")
@ConditionalOnProperty(prefix = "app.oauth", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class OAuthController {

    private final OAuthSignupChallengeStore signupChallengeStore;
    private final AuthUserJpaRepository     userRepo;
    private final PasswordHasher            passwordHasher;
    private final LoginExecutionService     loginExecutor;
    private final AuthCookieFactory         cookieFactory;
    private final AuthBehaviorProperties    behaviorProps;

    @PostMapping("/complete-signup")
    public ResponseEntity<?> completeSignup(@Valid @RequestBody OAuthCompleteSignupRequest request,
                                             HttpServletRequest httpRequest) {
        OAuthSignupChallengeEntry entry = signupChallengeStore.find(request.setupToken())
                .orElseThrow(UnauthorizedException::new);

        AuthUserEntity user = userRepo.findById(entry.userId())
                .filter(AuthUserEntity::isActive)
                .orElseThrow(UnauthorizedException::new);
        user.setPasswordHash(passwordHasher.hash(request.password()));
        userRepo.save(user);

        signupChallengeStore.delete(request.setupToken());

        String ip = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");
        LoginResult result = loginExecutor.proceedPostAuthentication(user, ip, userAgent, System.currentTimeMillis());

        log.info("oauth.signup.completed userId={}", user.getId());

        // Mirror AuthController.login()/MfaController.verifyLogin()'s
        // challenge-vs-cookie-vs-JSON branching exactly — completing signup
        // ends in a real login, and must honor the same MFA-gate and
        // auth.token-delivery-mode contracts every other login path does.
        if (result.mfaChallenge() != null) {
            return ResponseEntity.ok(new MfaLoginChallengeResponse(
                    true, result.mfaChallenge().challengeToken(), result.mfaChallenge().enrollmentRequired()));
        }

        if (behaviorProps.isJsonTokenDelivery()) {
            return ResponseEntity.ok(result.response());
        }

        ResponseCookie accessCookie = cookieFactory.createAccessTokenCookie(result.accessToken(), result.accessTokenTtl());
        ResponseCookie refreshCookie = cookieFactory.createRefreshTokenCookie(result.refreshToken(), result.refreshTokenTtl());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clearLegacyRefreshTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clearOldNarrowRefreshTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(result.response());
    }
}
