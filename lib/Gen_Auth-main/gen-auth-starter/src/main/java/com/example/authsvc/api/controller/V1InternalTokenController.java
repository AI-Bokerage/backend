package com.example.authsvc.api.controller;

import com.example.authsvc.api.dto.request.ClientTokenRequest;
import com.example.authsvc.api.dto.request.ImpersonationTokenRequest;
import com.example.authsvc.api.dto.response.ClientTokenResponse;
import com.example.authsvc.api.dto.response.ImpersonationTokenResponse;
import com.example.authsvc.application.service.ClientTokenService;
import com.example.authsvc.application.service.ImpersonationTokenService;
import com.example.authsvc.config.properties.InternalHmacAuthProperties;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HMAC-guarded internal token-mint endpoints, matching CPMS auth-svc's
 * {@code /v1/*} path shape. Guarded by
 * {@link com.example.authsvc.infrastructure.security.filter.InternalHmacAuthFilter}
 * via {@code app.internal-hmac-auth.target-paths} — not by JWT, not by the
 * shared-secret filter. See {@code SecurityConfig}'s permitAll entries for
 * these paths.
 *
 * <p>The controller itself only requires {@code app.internal-hmac-auth.enabled=true}
 * — each handler independently requires its own underlying feature flag via its
 * service bean being present ({@code app.super-admin.enabled} for impersonation-token,
 * {@code app.client-token.enabled} for client-token). A handler whose backing
 * service is absent returns 404 rather than existing half-configured.
 */
@Slf4j
@RestController
@RequestMapping("/v1")
@ConditionalOnProperty(prefix = "app.internal-hmac-auth", name = "enabled", havingValue = "true")
public class V1InternalTokenController {

    private final ImpersonationTokenService  impersonationTokenService;
    private final ClientTokenService         clientTokenService;
    private final InternalHmacAuthProperties internalHmacAuthProperties;

    public V1InternalTokenController(
            @Autowired(required = false) ImpersonationTokenService impersonationTokenService,
            @Autowired(required = false) ClientTokenService clientTokenService,
            InternalHmacAuthProperties internalHmacAuthProperties) {
        this.impersonationTokenService = impersonationTokenService;
        this.clientTokenService = clientTokenService;
        this.internalHmacAuthProperties = internalHmacAuthProperties;
    }

    @PostConstruct
    void assertGuardedByHmacFilter() {
        if (impersonationTokenService != null
                && !internalHmacAuthProperties.getTargetPaths().contains("/v1/impersonation-token")) {
            throw new IllegalStateException(
                    "app.internal-hmac-auth.target-paths must contain /v1/impersonation-token "
                            + "when app.internal-hmac-auth.enabled=true and app.super-admin.enabled=true — "
                            + "otherwise this endpoint is registered with no HMAC signature verification guarding it.");
        }
        if (clientTokenService != null
                && !internalHmacAuthProperties.getTargetPaths().contains("/v1/client-token")) {
            throw new IllegalStateException(
                    "app.internal-hmac-auth.target-paths must contain /v1/client-token "
                            + "when app.internal-hmac-auth.enabled=true and app.client-token.enabled=true — "
                            + "otherwise this endpoint is registered with no HMAC signature verification guarding it.");
        }
    }

    @PostMapping("/impersonation-token")
    public ResponseEntity<ImpersonationTokenResponse> issueImpersonationToken(
            @Valid @RequestBody ImpersonationTokenRequest request) {
        if (impersonationTokenService == null) {
            return ResponseEntity.notFound().build();
        }
        log.info("v1.impersonation.token.request superAdminId={} tenantId={} sessionId={}",
                request.superAdminId(), request.tenantId(), request.sessionId());
        return ResponseEntity.ok(impersonationTokenService.issue(request));
    }

    @PostMapping("/client-token")
    public ResponseEntity<ClientTokenResponse> issueClientToken(
            @Valid @RequestBody ClientTokenRequest request) {
        if (clientTokenService == null) {
            return ResponseEntity.notFound().build();
        }
        log.info("v1.client.token.request clientUserId={} tenantId={} sessionId={}",
                request.clientUserId(), request.tenantId(), request.sessionId());
        return ResponseEntity.ok(clientTokenService.issue(request));
    }
}
