package com.example.admsvc.application.impl;

import com.example.admsvc.application.service.RoleService;
import com.example.admsvc.common.exception.GenAdmConflictException;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.common.exception.GenAdmValidationException;
import com.example.admsvc.infrastructure.persistence.entity.PermissionEntity;
import com.example.admsvc.infrastructure.persistence.entity.RoleEntity;
import com.example.admsvc.infrastructure.persistence.repository.PermissionRepository;
import com.example.admsvc.infrastructure.persistence.repository.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public RoleServiceImpl(RoleRepository roleRepository, PermissionRepository permissionRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    @Override
    @Transactional
    public RoleEntity createRole(UUID tenantId, String name, String description) {
        roleRepository.findByTenantIdAndName(tenantId, name).ifPresent(existing -> {
            throw new GenAdmConflictException("Role '" + name + "' already exists for this tenant");
        });
        RoleEntity role = RoleEntity.builder()
                .tenantId(tenantId)
                .name(name)
                .description(description)
                .build();
        return roleRepository.save(role);
    }

    @Override
    @Transactional(readOnly = true)
    public RoleEntity getRole(UUID tenantId, UUID roleId) {
        return findOwnedRole(tenantId, roleId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleEntity> listRoles(UUID tenantId) {
        return roleRepository.findAllByTenantId(tenantId);
    }

    @Override
    @Transactional
    public RoleEntity grantPermissions(UUID tenantId, UUID roleId, Set<String> permissionCodes) {
        RoleEntity role = findOwnedRole(tenantId, roleId);
        for (String code : permissionCodes) {
            PermissionEntity permission = permissionRepository.findByCode(code)
                    .orElseThrow(() -> new GenAdmValidationException("Unknown permission code: " + code));
            role.getPermissions().add(permission);
        }
        return roleRepository.save(role);
    }

    @Override
    @Transactional
    public RoleEntity revokePermissions(UUID tenantId, UUID roleId, Set<String> permissionCodes) {
        RoleEntity role = findOwnedRole(tenantId, roleId);
        role.getPermissions().removeIf(p -> permissionCodes.contains(p.getCode()));
        return roleRepository.save(role);
    }

    @Override
    @Transactional
    public void deleteRole(UUID tenantId, UUID roleId) {
        RoleEntity role = findOwnedRole(tenantId, roleId);
        roleRepository.delete(role);
    }

    private RoleEntity findOwnedRole(UUID tenantId, UUID roleId) {
        RoleEntity role = roleRepository.findById(roleId)
                .orElseThrow(() -> new GenAdmNotFoundException("Role not found: " + roleId));
        if (!role.getTenantId().equals(tenantId)) {
            // Cross-tenant lookup surfaces as 404, never 403 — RLS would
            // already prevent this in a real transaction; this check keeps
            // the service correct even when called with a repository mock
            // in tests, or bypassing the aspect entirely.
            throw new GenAdmNotFoundException("Role not found: " + roleId);
        }
        return role;
    }
}
