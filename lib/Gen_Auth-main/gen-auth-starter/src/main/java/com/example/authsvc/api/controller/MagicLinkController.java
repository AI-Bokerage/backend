package com.example.authsvc.api.controller;

import com.example.authsvc.api.dto.request.MagicLinkIssueRequest;
import com.example.authsvc.api.dto.request.MagicLinkVerifyRequest;
import com.example.authsvc.api.dto.response.MagicLinkIssueResponse;
import com.example.authsvc.api.dto.response.MagicLinkVerifyResponse;
import com.example.authsvc.application.service.MagicLinkService;
import com.example.authsvc.infrastructure.security.cookie.AuthCookieFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Handles the forgot-password / magic-link reset flow. Only registered when
 * {@code app.magic-link.enabled=true}.
 *
 * <ul>
 *   <li>{@code POST /api/v1/auth/magic-link/issue}  — requests a password-reset link</li>
 *   <li>{@code POST /api/v1/auth/magic-link/verify} — validates the token and resets password</li>
 * </ul>
 *
 * Both endpoints are public (no JWT required) — permitted in
 * {@code SecurityConfig}'s permit-list (see Task 3, Step 12 in this plan).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth/magic-link")
@ConditionalOnProperty(prefix = "app.magic-link", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class MagicLinkController {

    private final MagicLinkService  magicLinkService;
    private final AuthCookieFactory cookieFactory;

    @PostMapping("/issue")
    public ResponseEntity<MagicLinkIssueResponse> issue(
            @Valid @RequestBody MagicLinkIssueRequest request,
            HttpServletRequest httpRequest) {

        String ip = httpRequest.getRemoteAddr();
        log.debug("magic_link.issue.request ip={}", ip);

        MagicLinkIssueResponse response = magicLinkService.issue(request, ip);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/verify")
    public ResponseEntity<MagicLinkVerifyResponse> verify(
            @Valid @RequestBody MagicLinkVerifyRequest request,
            HttpServletResponse httpResponse) {

        MagicLinkVerifyResponse response = magicLinkService.verify(request);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clearAccessTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clearRefreshTokenCookie().toString())
                .body(response);
    }
}
