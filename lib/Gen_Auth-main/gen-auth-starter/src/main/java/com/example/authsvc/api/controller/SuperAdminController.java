package com.example.authsvc.api.controller;

import com.example.authsvc.api.dto.request.LoginRequest;
import com.example.authsvc.api.dto.request.MagicLinkIssueRequest;
import com.example.authsvc.api.dto.request.MagicLinkVerifyRequest;
import com.example.authsvc.api.dto.response.LoginResponse;
import com.example.authsvc.api.dto.response.LoginResult;
import com.example.authsvc.api.dto.response.MagicLinkIssueResponse;
import com.example.authsvc.api.dto.response.MagicLinkVerifyResponse;
import com.example.authsvc.application.service.SuperAdminLoginService;
import com.example.authsvc.application.service.SuperAdminMagicLinkService;
import com.example.authsvc.infrastructure.security.cookie.AuthCookieFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform super admin login and password reset. Only registered when
 * {@code app.super-admin.enabled=true}.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth/super-admin")
@ConditionalOnProperty(prefix = "app.super-admin", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class SuperAdminController {

    private final SuperAdminLoginService     superAdminLoginService;
    private final SuperAdminMagicLinkService superAdminMagicLinkService;
    private final AuthCookieFactory          cookieFactory;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        long startNs = System.nanoTime();

        String ipAddress = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");

        log.debug("superadmin.login.request email={} ip={}", request.getEmail(), ipAddress);

        LoginResult result = superAdminLoginService.login(request, ipAddress, userAgent);

        long latencyMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("superadmin.login.response userId={} latencyMs={}",
                result.response().getUserId(), latencyMs);

        ResponseCookie accessCookie = cookieFactory.createAccessTokenCookie(
                result.accessToken(), result.accessTokenTtl());
        ResponseCookie refreshCookie = cookieFactory.createRefreshTokenCookie(
                result.refreshToken(), result.refreshTokenTtl());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clearLegacyRefreshTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clearOldNarrowRefreshTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(result.response());
    }

    @PostMapping("/reset-password/issue")
    public ResponseEntity<MagicLinkIssueResponse> issueResetPassword(
            @Valid @RequestBody MagicLinkIssueRequest request,
            HttpServletRequest httpRequest) {

        String ip = httpRequest.getRemoteAddr();
        log.debug("superadmin.magic_link.issue.request ip={}", ip);

        MagicLinkIssueResponse response = superAdminMagicLinkService.issue(request, ip);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/reset-password/verify")
    public ResponseEntity<MagicLinkVerifyResponse> verifyResetPassword(
            @Valid @RequestBody MagicLinkVerifyRequest request,
            HttpServletResponse httpResponse) {

        MagicLinkVerifyResponse response = superAdminMagicLinkService.verify(request);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clearAccessTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clearRefreshTokenCookie().toString())
                .body(response);
    }
}
