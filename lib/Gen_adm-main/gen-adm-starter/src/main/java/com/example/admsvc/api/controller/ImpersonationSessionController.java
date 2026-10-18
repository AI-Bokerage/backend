package com.example.admsvc.api.controller;

import com.example.admsvc.api.dto.request.CreateImpersonationRequest;
import com.example.admsvc.api.dto.response.ImpersonationSessionResponse;
import com.example.admsvc.application.service.ImpersonationSessionService;
import com.example.admsvc.application.service.PermissionChecker;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/impersonation-requests")
public class ImpersonationSessionController {

    private static final String REQUEST_IMPERSONATION = "adm:impersonation:request";
    private static final String MANAGE_IMPERSONATION = "adm:impersonation:manage";

    private final ImpersonationSessionService impersonationSessionService;
    private final PermissionChecker permissionChecker;

    public ImpersonationSessionController(ImpersonationSessionService impersonationSessionService,
                                           PermissionChecker permissionChecker) {
        this.impersonationSessionService = impersonationSessionService;
        this.permissionChecker = permissionChecker;
    }

    @PostMapping
    public ImpersonationSessionResponse create(@AuthenticationPrincipal GenAdmPrincipal principal,
                                                @Valid @RequestBody CreateImpersonationRequest request) {
        permissionChecker.require(principal, REQUEST_IMPERSONATION);
        return ImpersonationSessionResponse.from(impersonationSessionService.create(
                principal.tenantId(), principal.userId(), request.targetUserId(),
                request.reason(), request.ttlMinutes()));
    }

    @GetMapping
    public List<ImpersonationSessionResponse> list(@AuthenticationPrincipal GenAdmPrincipal principal) {
        permissionChecker.require(principal, MANAGE_IMPERSONATION);
        return impersonationSessionService.listActionable(principal.tenantId()).stream()
                .map(ImpersonationSessionResponse::from)
                .toList();
    }

    @PostMapping("/{sessionId}/approve")
    public ImpersonationSessionResponse approve(@AuthenticationPrincipal GenAdmPrincipal principal,
                                                 @PathVariable UUID sessionId) {
        permissionChecker.require(principal, MANAGE_IMPERSONATION);
        return ImpersonationSessionResponse.from(
                impersonationSessionService.approve(principal.tenantId(), sessionId, principal.userId()));
    }

    @PostMapping("/{sessionId}/reject")
    public ImpersonationSessionResponse reject(@AuthenticationPrincipal GenAdmPrincipal principal,
                                                @PathVariable UUID sessionId) {
        permissionChecker.require(principal, MANAGE_IMPERSONATION);
        return ImpersonationSessionResponse.from(
                impersonationSessionService.reject(principal.tenantId(), sessionId, principal.userId()));
    }

    @PostMapping("/{sessionId}/end")
    public ImpersonationSessionResponse end(@AuthenticationPrincipal GenAdmPrincipal principal,
                                             @PathVariable UUID sessionId) {
        boolean privileged = permissionChecker.has(principal, MANAGE_IMPERSONATION);
        return ImpersonationSessionResponse.from(
                impersonationSessionService.end(principal.tenantId(), sessionId, principal.userId(), privileged));
    }
}
