package com.example.admsvc.api.controller;

import com.example.admsvc.api.dto.request.CreateRoleRequest;
import com.example.admsvc.api.dto.request.GrantPermissionsRequest;
import com.example.admsvc.api.dto.response.RoleResponse;
import com.example.admsvc.application.service.PermissionChecker;
import com.example.admsvc.application.service.RoleService;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    private static final String MANAGE_ROLES = "adm:roles:manage";

    private final RoleService roleService;
    private final PermissionChecker permissionChecker;

    public RoleController(RoleService roleService, PermissionChecker permissionChecker) {
        this.roleService = roleService;
        this.permissionChecker = permissionChecker;
    }

    @PostMapping
    public RoleResponse create(@AuthenticationPrincipal GenAdmPrincipal principal,
                                @Valid @RequestBody CreateRoleRequest request) {
        permissionChecker.require(principal, MANAGE_ROLES);
        return RoleResponse.from(roleService.createRole(principal.tenantId(), request.name(), request.description()));
    }

    @GetMapping
    public List<RoleResponse> list(@AuthenticationPrincipal GenAdmPrincipal principal) {
        permissionChecker.require(principal, MANAGE_ROLES);
        return roleService.listRoles(principal.tenantId()).stream().map(RoleResponse::from).toList();
    }

    @GetMapping("/{roleId}")
    public RoleResponse get(@AuthenticationPrincipal GenAdmPrincipal principal, @PathVariable UUID roleId) {
        permissionChecker.require(principal, MANAGE_ROLES);
        return RoleResponse.from(roleService.getRole(principal.tenantId(), roleId));
    }

    @DeleteMapping("/{roleId}")
    public void delete(@AuthenticationPrincipal GenAdmPrincipal principal, @PathVariable UUID roleId) {
        permissionChecker.require(principal, MANAGE_ROLES);
        roleService.deleteRole(principal.tenantId(), roleId);
    }

    @PutMapping("/{roleId}/permissions")
    public RoleResponse replacePermissions(@AuthenticationPrincipal GenAdmPrincipal principal,
                                            @PathVariable UUID roleId,
                                            @Valid @RequestBody GrantPermissionsRequest request) {
        permissionChecker.require(principal, MANAGE_ROLES);
        return RoleResponse.from(roleService.grantPermissions(principal.tenantId(), roleId, request.permissionCodes()));
    }
}
