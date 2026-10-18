package com.example.authsvc.api.controller;

import com.example.authsvc.api.dto.request.ChangePasswordRequest;
import com.example.authsvc.api.dto.request.LoginRequest;
import com.example.authsvc.api.dto.request.RegisterRequest;
import com.example.authsvc.api.dto.response.LoginResponse;
import com.example.authsvc.api.dto.response.LoginResult;
import com.example.authsvc.api.dto.response.LogoutResponse;
import com.example.authsvc.api.dto.response.MfaLoginChallengeResponse;
import com.example.authsvc.api.dto.response.RefreshResponse;
import com.example.authsvc.api.dto.response.RefreshResult;
import com.example.authsvc.api.dto.response.RegisterResponse;
import com.example.authsvc.api.dto.response.SessionResponse;
import com.example.authsvc.application.service.ChangePasswordService;
import com.example.authsvc.common.exception.RegistrationDisabledException;
import com.example.authsvc.common.exception.UnauthorizedException;
import com.example.authsvc.application.service.LoginService;
import com.example.authsvc.application.service.RefreshTokenService;
import com.example.authsvc.application.service.RegisterService;
import com.example.authsvc.application.service.SessionService;
import com.example.authsvc.application.impl.InternalSessionRevocationService;
import com.example.authsvc.config.properties.AuthBehaviorProperties;
import com.example.authsvc.infrastructure.security.cookie.AuthCookieFactory;
import com.example.authsvc.infrastructure.security.principal.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginService           loginService;
    private final RefreshTokenService refreshTokenService;
    private final SessionService    sessionService;
    private final ChangePasswordService changePasswordService;
    private final AuthCookieFactory cookieFactory;
    private final AuthBehaviorProperties behaviorProps;
    private final RegisterService   registerService;
    private final InternalSessionRevocationService revocationService;

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        long startNs = System.nanoTime();

        String ipAddress = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");

        log.debug("login.request email={} ip={}", request.getEmail(), ipAddress);

        LoginResult result = loginService.login(request, ipAddress, userAgent);

        if (result.mfaChallenge() != null) {
            log.info("login.mfa_challenge_issued");
            return ResponseEntity.ok(new MfaLoginChallengeResponse(
                    true, result.mfaChallenge().challengeToken(), result.mfaChallenge().enrollmentRequired()));
        }

        long latencyMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("login.response userId={} email={} latencyMs={}",
                result.response().getUserId(), result.response().getEmail(), latencyMs);
        log.info("perf.controller.login.ms={}", latencyMs);

        if (behaviorProps.isJsonTokenDelivery()) {
            LoginResponse body = new LoginResponse(
                    result.response().getUserId(),
                    result.response().getEmail(),
                    result.response().getAccessTokenExpiresAt(),
                    result.accessToken(),
                    result.refreshToken());
            return ResponseEntity.ok(body);
        }

        ResponseCookie accessCookie  = cookieFactory.createAccessTokenCookie(
                result.accessToken(),  result.accessTokenTtl());
        ResponseCookie refreshCookie = cookieFactory.createRefreshTokenCookie(
                result.refreshToken(), result.refreshTokenTtl());

        // Clear old cookies at legacy paths before issuing the new token.
        // Order matters: clear-cookies are sent FIRST so that any "last-writer-wins"
        // client (e.g. Postman) ends up with the new token, not an empty value.
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clearLegacyRefreshTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clearOldNarrowRefreshTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(result.response());
    }

    @PostMapping("/logout")
    public ResponseEntity<LogoutResponse> logout(
            @CookieValue(name = AuthCookieFactory.REFRESH_TOKEN_COOKIE, required = false)
            String rawRefreshToken,
            HttpServletRequest httpRequest) {

        String ip = httpRequest.getRemoteAddr();
        String ua = httpRequest.getHeader("User-Agent");

        refreshTokenService.logout(rawRefreshToken, ip, ua);

        log.info("auth.logout ip={}", ip);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clearAccessTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clearRefreshTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clearLegacyRefreshTokenCookie().toString())
                .body(LogoutResponse.success());
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        if (!behaviorProps.isRegistrationOpen()) {
            throw new RegistrationDisabledException("Self-registration is disabled");
        }
        UUID userId = registerService.register(
                request.getEmail(), request.getPassword(), request.getTenantId(), null, null, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(new RegisterResponse(userId));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ChangePasswordRequest request) {

        changePasswordService.changePassword(principal, request);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<LogoutResponse> logoutAll(
            @AuthenticationPrincipal AuthenticatedUser principal) {

        revocationService.revokeAllForUser(principal.getUserId(), principal.getTenantId());
        log.info("auth.logout_all userId={}", principal.getUserId());

        if (behaviorProps.isJsonTokenDelivery()) {
            return ResponseEntity.ok(LogoutResponse.success());
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clearAccessTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clearRefreshTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clearLegacyRefreshTokenCookie().toString())
                .body(LogoutResponse.success());
    }

    @GetMapping("/session")
    public ResponseEntity<SessionResponse> session(
            @AuthenticationPrincipal AuthenticatedUser principal,
            HttpServletResponse httpResponse) {

        return ResponseEntity.ok(sessionService.getSession(principal, httpResponse));
    }



    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(
            @CookieValue(name = AuthCookieFactory.REFRESH_TOKEN_COOKIE, required = false)
            String rawRefreshToken,
            HttpServletRequest httpRequest) {

        long startMs = System.currentTimeMillis();
        String ip = httpRequest.getRemoteAddr();
        String ua = httpRequest.getHeader("User-Agent");

        RefreshResult result;
        try {
            result = refreshTokenService.refresh(rawRefreshToken, ip, ua);
        } catch (UnauthorizedException e) {
            // Token invalid, expired, or replay detected — clear cookies so the
            // browser doesn't keep sending a stale (or stolen) refresh token.
            log.debug("refresh.unauthorized ip={} — clearing cookies", ip);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, cookieFactory.clearAccessTokenCookie().toString())
                    .header(HttpHeaders.SET_COOKIE, cookieFactory.clearRefreshTokenCookie().toString())
                    .header(HttpHeaders.SET_COOKIE, cookieFactory.clearLegacyRefreshTokenCookie().toString())
                    .build();
        }
        log.info("perf.controller.refresh.ms={}", System.currentTimeMillis() - startMs);

        if (behaviorProps.isJsonTokenDelivery()) {
            RefreshResponse body = new RefreshResponse(
                    result.response().accessTokenExpiresAt(),
                    result.accessToken(),
                    result.refreshToken());
            return ResponseEntity.ok(body);
        }

        ResponseCookie accessCookie  = cookieFactory.createAccessTokenCookie(
                result.accessToken(), result.accessTokenTtl());
        ResponseCookie refreshCookie = cookieFactory.createRefreshTokenCookie(
                result.refreshToken(), result.refreshTokenTtl());

        // Clear old cookies at legacy paths before issuing the new token.
        // Order matters: clear-cookies are sent FIRST so that any "last-writer-wins"
        // client (e.g. Postman) ends up with the new token, not an empty value.
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clearLegacyRefreshTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clearOldNarrowRefreshTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(result.response());
    }
}
