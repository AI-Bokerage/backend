package com.example.admsvc.application.service;

import com.example.admsvc.infrastructure.persistence.entity.RoleEntity;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface RoleService {

    RoleEntity createRole(UUID tenantId, String name, String description);

    RoleEntity getRole(UUID tenantId, UUID roleId);

    List<RoleEntity> listRoles(UUID tenantId);

    RoleEntity grantPermissions(UUID tenantId, UUID roleId, Set<String> permissionCodes);

    RoleEntity revokePermissions(UUID tenantId, UUID roleId, Set<String> permissionCodes);

    void deleteRole(UUID tenantId, UUID roleId);
}
