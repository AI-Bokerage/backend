package com.example.admsvc.api.controller;

import com.example.admsvc.api.dto.request.CreateSupportTicketRequest;
import com.example.admsvc.api.dto.response.SupportTicketResponse;
import com.example.admsvc.application.service.PermissionChecker;
import com.example.admsvc.application.service.SupportTicketService;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/support-tickets")
public class SupportTicketController {

    private static final String MANAGE_TICKETS = "adm:tickets:manage";

    private final SupportTicketService supportTicketService;
    private final PermissionChecker permissionChecker;

    public SupportTicketController(SupportTicketService supportTicketService, PermissionChecker permissionChecker) {
        this.supportTicketService = supportTicketService;
        this.permissionChecker = permissionChecker;
    }

    @PostMapping
    public SupportTicketResponse create(@AuthenticationPrincipal GenAdmPrincipal principal,
                                         @Valid @RequestBody CreateSupportTicketRequest request) {
        return SupportTicketResponse.from(supportTicketService.create(
                principal.tenantId(), principal.userId(), request.type(),
                request.subject(), request.description(), request.priority()));
    }

    @GetMapping("/mine")
    public List<SupportTicketResponse> listMine(@AuthenticationPrincipal GenAdmPrincipal principal) {
        return supportTicketService.listMine(principal.tenantId(), principal.userId()).stream()
                .map(SupportTicketResponse::from)
                .toList();
    }

    @GetMapping
    public List<SupportTicketResponse> listAll(@AuthenticationPrincipal GenAdmPrincipal principal) {
        permissionChecker.require(principal, MANAGE_TICKETS);
        return supportTicketService.listAll(principal.tenantId()).stream()
                .map(SupportTicketResponse::from)
                .toList();
    }

    @PostMapping("/{id}/start")
    public SupportTicketResponse start(@AuthenticationPrincipal GenAdmPrincipal principal,
                                        @PathVariable UUID id) {
        permissionChecker.require(principal, MANAGE_TICKETS);
        return SupportTicketResponse.from(supportTicketService.start(principal.tenantId(), id));
    }

    @PostMapping("/{id}/resolve")
    public SupportTicketResponse resolve(@AuthenticationPrincipal GenAdmPrincipal principal,
                                          @PathVariable UUID id) {
        permissionChecker.require(principal, MANAGE_TICKETS);
        return SupportTicketResponse.from(
                supportTicketService.resolve(principal.tenantId(), id, principal.userId()));
    }

    @PostMapping("/{id}/close")
    public SupportTicketResponse close(@AuthenticationPrincipal GenAdmPrincipal principal,
                                        @PathVariable UUID id) {
        permissionChecker.require(principal, MANAGE_TICKETS);
        return SupportTicketResponse.from(
                supportTicketService.close(principal.tenantId(), id, principal.userId()));
    }
}
