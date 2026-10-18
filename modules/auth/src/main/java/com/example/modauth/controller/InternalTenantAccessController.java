package com.example.modauth.controller;

import com.example.modauth.dto.TenantAccessRequest;
import com.example.modauth.service.TenantAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Called by modules/platform when a brokerage is suspended, reactivated or cancelled (Flow 1). Internal-secret gated. */
@Tag(name = "Internal — Tenant access", description = "Service-to-service: block or restore a brokerage's sign-in")
@RestController
@RequestMapping("/internal/modauth/tenants")
@RequiredArgsConstructor
public class InternalTenantAccessController {

    private final TenantAccessService tenantAccessService;

    @Operation(summary = "Block or restore sign-in for a brokerage",
            description = "Blocking also revokes every refresh token and session of that brokerage's people.")
    @PutMapping("/{tenantId}/access")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setAccess(@PathVariable UUID tenantId, @Valid @RequestBody TenantAccessRequest request) {
        tenantAccessService.setAccess(tenantId, request.blocked(), request.status());
    }
}
