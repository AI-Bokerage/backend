package com.example.admsvc.api.controller;

import com.example.admsvc.api.dto.request.AcceptInvitationRequest;
import com.example.admsvc.api.dto.request.CreateInvitationRequest;
import com.example.admsvc.api.dto.response.InvitationResponse;
import com.example.admsvc.application.service.InvitationService;
import com.example.admsvc.application.service.PermissionChecker;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import com.example.admsvc.infrastructure.persistence.entity.InvitationEntity;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invitations")
public class InvitationController {

    private static final String MANAGE_INVITATIONS = "adm:invitations:manage";

    private final InvitationService invitationService;
    private final PermissionChecker permissionChecker;

    public InvitationController(InvitationService invitationService, PermissionChecker permissionChecker) {
        this.invitationService = invitationService;
        this.permissionChecker = permissionChecker;
    }

    @PostMapping
    public InvitationResponse create(@AuthenticationPrincipal GenAdmPrincipal principal,
                                      @Valid @RequestBody CreateInvitationRequest request) {
        permissionChecker.require(principal, MANAGE_INVITATIONS);
        InvitationEntity created = invitationService.create(
                principal.tenantId(), principal.userId(), request.email(), request.roleIds());
        return InvitationResponse.forCreate(created, created.getPlaintextToken());
    }

    @GetMapping
    public List<InvitationResponse> list(@AuthenticationPrincipal GenAdmPrincipal principal) {
        permissionChecker.require(principal, MANAGE_INVITATIONS);
        return invitationService.listActionable(principal.tenantId()).stream()
                .map(InvitationResponse::from)
                .toList();
    }

    @PostMapping("/{id}/cancel")
    public InvitationResponse cancel(@AuthenticationPrincipal GenAdmPrincipal principal,
                                      @PathVariable UUID id) {
        permissionChecker.require(principal, MANAGE_INVITATIONS);
        return InvitationResponse.from(
                invitationService.cancel(principal.tenantId(), id, principal.userId()));
    }

    @PostMapping("/{token}/accept")
    public InvitationResponse accept(@PathVariable String token,
                                      @Valid @RequestBody AcceptInvitationRequest request) {
        return InvitationResponse.from(invitationService.accept(token, request.userId()));
    }
}
