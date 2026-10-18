package com.example.admsvc.api.controller;

import com.example.admsvc.api.dto.request.AssignRoleRequest;
import com.example.admsvc.api.dto.response.AssignmentResponse;
import com.example.admsvc.application.service.PermissionChecker;
import com.example.admsvc.application.service.UserRoleAssignmentService;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assignments")
public class AssignmentController {

    private static final String MANAGE_ROLES = "adm:roles:manage";

    private final UserRoleAssignmentService assignmentService;
    private final PermissionChecker permissionChecker;

    public AssignmentController(UserRoleAssignmentService assignmentService, PermissionChecker permissionChecker) {
        this.assignmentService = assignmentService;
        this.permissionChecker = permissionChecker;
    }

    @PostMapping
    public AssignmentResponse assign(@AuthenticationPrincipal GenAdmPrincipal principal,
                                      @Valid @RequestBody AssignRoleRequest request) {
        permissionChecker.require(principal, MANAGE_ROLES);
        return AssignmentResponse.from(
                assignmentService.assignRole(principal.tenantId(), request.userId(), request.roleId()));
    }

    @DeleteMapping
    public void revoke(@AuthenticationPrincipal GenAdmPrincipal principal,
                        @RequestParam UUID userId, @RequestParam UUID roleId) {
        permissionChecker.require(principal, MANAGE_ROLES);
        assignmentService.revokeRole(principal.tenantId(), userId, roleId);
    }

    @GetMapping
    public List<AssignmentResponse> list(@AuthenticationPrincipal GenAdmPrincipal principal,
                                          @RequestParam UUID userId) {
        permissionChecker.require(principal, MANAGE_ROLES);
        return assignmentService.listAssignments(principal.tenantId(), userId).stream()
                .map(AssignmentResponse::from).toList();
    }
}
