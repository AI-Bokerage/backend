package com.example.authsvc.api.controller;

import com.example.authsvc.api.dto.request.ImpersonationTokenRequest;
import com.example.authsvc.api.dto.response.ImpersonationTokenResponse;
import com.example.authsvc.application.service.ImpersonationTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal endpoint for issuing impersonation JWTs. Only registered when
 * {@code app.super-admin.enabled=true}.
 *
 * <p>Protected by {@code X-Internal-Secret} via
 * {@link com.example.authsvc.infrastructure.security.filter.InternalTokenAuthFilter}
 * through the {@code /internal/**} path prefix — the same mechanism already
 * guarding {@code /internal/auth/users} and {@code /internal/auth/keys/**}.
 * No user JWT is required or checked.
 */
@Slf4j
@RestController
@RequestMapping("/internal/auth")
@ConditionalOnProperty(prefix = "app.super-admin", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class ImpersonationTokenController {

    private final ImpersonationTokenService impersonationTokenService;

    @PostMapping("/impersonation-token")
    public ResponseEntity<ImpersonationTokenResponse> issueImpersonationToken(
            @Valid @RequestBody ImpersonationTokenRequest request) {

        log.info("impersonation.token.request superAdminId={} tenantId={} sessionId={}",
                request.superAdminId(), request.tenantId(), request.sessionId());

        ImpersonationTokenResponse response = impersonationTokenService.issue(request);

        return ResponseEntity.ok(response);
    }
}
