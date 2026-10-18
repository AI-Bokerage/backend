package com.example.admsvc.application.impl;

import com.example.admsvc.application.service.UserRoleAssignmentService;
import com.example.admsvc.common.exception.GenAdmConflictException;
import com.example.admsvc.common.exception.GenAdmValidationException;
import com.example.admsvc.domain.port.TenantIdParam;
import com.example.admsvc.infrastructure.persistence.entity.PermissionEntity;
import com.example.admsvc.infrastructure.persistence.entity.RoleEntity;
import com.example.admsvc.infrastructure.persistence.entity.UserRoleAssignmentEntity;
import com.example.admsvc.infrastructure.persistence.repository.PermissionRepository;
import com.example.admsvc.infrastructure.persistence.repository.RoleRepository;
import com.example.admsvc.infrastructure.persistence.repository.UserRoleAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class UserRoleAssignmentServiceImpl implements UserRoleAssignmentService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRoleAssignmentRepository assignmentRepository;

    public UserRoleAssignmentServiceImpl(
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            UserRoleAssignmentRepository assignmentRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.assignmentRepository = assignmentRepository;
    }

    @Override
    @Transactional
    public UserRoleAssignmentEntity assignRole(UUID tenantId, UUID userId, UUID roleId) {
        assignmentRepository.findByTenantIdAndUserIdAndRoleId(tenantId, userId, roleId).ifPresent(existing -> {
            throw new GenAdmConflictException("User already holds this role");
        });
        UserRoleAssignmentEntity assignment = UserRoleAssignmentEntity.builder()
                .tenantId(tenantId)
                .userId(userId)
                .roleId(roleId)
                .build();
        return assignmentRepository.save(assignment);
    }

    @Override
    @Transactional
    public void revokeRole(UUID tenantId, UUID userId, UUID roleId) {
        assignmentRepository.deleteByTenantIdAndUserIdAndRoleId(tenantId, userId, roleId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserRoleAssignmentEntity> listAssignments(UUID tenantId, UUID userId) {
        return assignmentRepository.findAllByTenantIdAndUserId(tenantId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> effectivePermissionCodes(UUID tenantId, UUID userId) {
        List<UserRoleAssignmentEntity> assignments = assignmentRepository.findAllByTenantIdAndUserId(tenantId, userId);
        Set<String> codes = new HashSet<>();
        for (UserRoleAssignmentEntity assignment : assignments) {
            roleRepository.findById(assignment.getRoleId()).ifPresent(role ->
                    role.getPermissions().forEach(p -> codes.add(p.getCode())));
        }
        return codes;
    }

    @Override
    @Transactional
    public UserRoleAssignmentEntity bootstrapTenant(
            @TenantIdParam UUID tenantId, UUID userId, String roleName, Set<String> permissionCodes) {
        if (roleRepository.existsByTenantId(tenantId)) {
            throw new GenAdmConflictException(
                    "Tenant " + tenantId + " already has roles — bootstrapTenant is for first-role creation only");
        }
        RoleEntity role = RoleEntity.builder()
                .tenantId(tenantId)
                .name(roleName)
                .build();
        for (String code : permissionCodes) {
            PermissionEntity permission = permissionRepository.findByCode(code)
                    .orElseThrow(() -> new GenAdmValidationException("Unknown permission code: " + code));
            role.getPermissions().add(permission);
        }
        RoleEntity savedRole = roleRepository.save(role);

        UserRoleAssignmentEntity assignment = UserRoleAssignmentEntity.builder()
                .tenantId(tenantId)
                .userId(userId)
                .roleId(savedRole.getId())
                .build();
        return assignmentRepository.save(assignment);
    }

    @Override
    @Transactional
    public UserRoleAssignmentEntity assignRoleFromInvitation(@TenantIdParam UUID tenantId, UUID userId, UUID roleId) {
        return assignRole(tenantId, userId, roleId);
    }
}
