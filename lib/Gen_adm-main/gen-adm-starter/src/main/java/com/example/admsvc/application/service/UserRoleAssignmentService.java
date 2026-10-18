package com.example.admsvc.application.service;

import com.example.admsvc.domain.port.TenantIdParam;
import com.example.admsvc.infrastructure.persistence.entity.UserRoleAssignmentEntity;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface UserRoleAssignmentService {

    UserRoleAssignmentEntity assignRole(UUID tenantId, UUID userId, UUID roleId);

    void revokeRole(UUID tenantId, UUID userId, UUID roleId);

    List<UserRoleAssignmentEntity> listAssignments(UUID tenantId, UUID userId);

    Set<String> effectivePermissionCodes(UUID tenantId, UUID userId);

    /**
     * Programmatic-only, permission-check-free path for a fresh tenant's
     * first role — no HTTP route exists for this. Callable from a host
     * app's own tenant-provisioning flow with no authenticated principal
     * in context, which is why {@code tenantId} is {@code @TenantIdParam}-
     * annotated: {@link com.example.admsvc.infrastructure.security.TenantContextAspect}
     * uses it directly instead of looking for a principal.
     * Throws {@link com.example.admsvc.common.exception.GenAdmConflictException}
     * if the tenant already has at least one role.
     */
    UserRoleAssignmentEntity bootstrapTenant(
            @TenantIdParam UUID tenantId, UUID userId, String roleName, Set<String> permissionCodes);

    /**
     * Same assignment as {@link #assignRole}, for the invitation-accept
     * flow — which, like {@link #bootstrapTenant}, has no authenticated
     * principal in scope (it's a public, token-authenticated endpoint).
     * {@code tenantId} is resolved by the caller (from the invitation row
     * itself) and passed explicitly for {@code TenantContextAspect} to use.
     */
    UserRoleAssignmentEntity assignRoleFromInvitation(@TenantIdParam UUID tenantId, UUID userId, UUID roleId);
}
