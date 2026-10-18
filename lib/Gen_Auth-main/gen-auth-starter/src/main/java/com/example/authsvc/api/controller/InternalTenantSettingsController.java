package com.example.authsvc.api.controller;

import com.example.authsvc.api.dto.request.TenantMfaRequiredRequest;
import com.example.authsvc.application.service.TenantSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Internal endpoint for setting per-tenant MFA enforcement. Protected by
 * {@link com.example.authsvc.infrastructure.security.filter.InternalTokenAuthFilter}
 * (shared-secret header) via the {@code /internal/**} path prefix — same
 * mechanism already guarding {@code /internal/auth/users} and
 * {@code /internal/auth/keys/**}. Not gated by {@code app.mfa.enabled} —
 * always registered, matching {@code InternalUserController}; setting the
 * flag when MFA is globally disabled is harmless (nothing reads it).
 */
@Slf4j
@RestController
@RequestMapping("/internal/auth/tenants")
@RequiredArgsConstructor
public class InternalTenantSettingsController {

    private final TenantSettingsService tenantSettingsService;

    @PostMapping("/{tenantId}/mfa-required")
    public ResponseEntity<Void> setMfaRequired(
            @PathVariable("tenantId") UUID tenantId,
            @Valid @RequestBody TenantMfaRequiredRequest request) {
        tenantSettingsService.setMfaRequired(tenantId, request.required());
        log.info("internal.tenant_mfa_required.set tenantId={} required={}", tenantId, request.required());
        return ResponseEntity.noContent().build();
    }
}
