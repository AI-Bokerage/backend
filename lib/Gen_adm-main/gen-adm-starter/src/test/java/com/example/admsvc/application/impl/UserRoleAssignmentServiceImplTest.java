package com.example.admsvc.application.impl;

import com.example.admsvc.common.exception.GenAdmConflictException;
import com.example.admsvc.infrastructure.persistence.entity.PermissionEntity;
import com.example.admsvc.infrastructure.persistence.entity.RoleEntity;
import com.example.admsvc.infrastructure.persistence.entity.UserRoleAssignmentEntity;
import com.example.admsvc.infrastructure.persistence.repository.PermissionRepository;
import com.example.admsvc.infrastructure.persistence.repository.RoleRepository;
import com.example.admsvc.infrastructure.persistence.repository.UserRoleAssignmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRoleAssignmentServiceImplTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private UserRoleAssignmentRepository assignmentRepository;

    @InjectMocks
    private UserRoleAssignmentServiceImpl service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    void assignsARoleToAUser() {
        UUID roleId = UUID.randomUUID();
        when(assignmentRepository.findByTenantIdAndUserIdAndRoleId(tenantId, userId, roleId))
                .thenReturn(Optional.empty());
        when(assignmentRepository.save(any(UserRoleAssignmentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UserRoleAssignmentEntity assignment = service.assignRole(tenantId, userId, roleId);

        assertThat(assignment.getTenantId()).isEqualTo(tenantId);
        assertThat(assignment.getUserId()).isEqualTo(userId);
        assertThat(assignment.getRoleId()).isEqualTo(roleId);
    }

    @Test
    void rejectsAssigningTheSameRoleToTheSameUserTwice() {
        UUID roleId = UUID.randomUUID();
        when(assignmentRepository.findByTenantIdAndUserIdAndRoleId(tenantId, userId, roleId))
                .thenReturn(Optional.of(UserRoleAssignmentEntity.builder()
                        .tenantId(tenantId).userId(userId).roleId(roleId).build()));

        assertThatThrownBy(() -> service.assignRole(tenantId, userId, roleId))
                .isInstanceOf(GenAdmConflictException.class);

        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void listsEffectivePermissionCodesAcrossAllAssignedRoles() {
        RoleEntity roleA = RoleEntity.builder().id(UUID.randomUUID()).tenantId(tenantId).name("a").build();
        roleA.getPermissions().add(PermissionEntity.builder().code("users:read").build());
        RoleEntity roleB = RoleEntity.builder().id(UUID.randomUUID()).tenantId(tenantId).name("b").build();
        roleB.getPermissions().add(PermissionEntity.builder().code("billing:manage").build());

        when(assignmentRepository.findAllByTenantIdAndUserId(tenantId, userId)).thenReturn(List.of(
                UserRoleAssignmentEntity.builder().tenantId(tenantId).userId(userId).roleId(roleA.getId()).build(),
                UserRoleAssignmentEntity.builder().tenantId(tenantId).userId(userId).roleId(roleB.getId()).build()
        ));
        when(roleRepository.findById(roleA.getId())).thenReturn(Optional.of(roleA));
        when(roleRepository.findById(roleB.getId())).thenReturn(Optional.of(roleB));

        Set<String> codes = service.effectivePermissionCodes(tenantId, userId);

        assertThat(codes).containsExactlyInAnyOrder("users:read", "billing:manage");
    }

    @Test
    void bootstrapCreatesFirstRoleWhenTenantHasNone() {
        when(roleRepository.existsByTenantId(tenantId)).thenReturn(false);
        when(roleRepository.save(any(RoleEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(permissionRepository.findByCode("adm:roles:manage"))
                .thenReturn(Optional.of(PermissionEntity.builder().code("adm:roles:manage").build()));
        when(assignmentRepository.save(any(UserRoleAssignmentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UserRoleAssignmentEntity assignment = service.bootstrapTenant(
                tenantId, userId, "owner", Set.of("adm:roles:manage"));

        assertThat(assignment.getTenantId()).isEqualTo(tenantId);
        assertThat(assignment.getUserId()).isEqualTo(userId);
        verify(roleRepository).save(any(RoleEntity.class));
    }

    @Test
    void bootstrapRejectsATenantThatAlreadyHasRoles() {
        when(roleRepository.existsByTenantId(tenantId)).thenReturn(true);

        assertThatThrownBy(() -> service.bootstrapTenant(tenantId, userId, "owner", Set.of("adm:roles:manage")))
                .isInstanceOf(GenAdmConflictException.class);

        verify(roleRepository, never()).save(any());
    }

    @Test
    void assignRoleFromInvitationDelegatesToAssignRole() {
        UUID roleId = UUID.randomUUID();
        when(assignmentRepository.findByTenantIdAndUserIdAndRoleId(tenantId, userId, roleId))
                .thenReturn(Optional.empty());
        when(assignmentRepository.save(any(UserRoleAssignmentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UserRoleAssignmentEntity assignment = service.assignRoleFromInvitation(tenantId, userId, roleId);

        assertThat(assignment.getTenantId()).isEqualTo(tenantId);
        assertThat(assignment.getUserId()).isEqualTo(userId);
        assertThat(assignment.getRoleId()).isEqualTo(roleId);
    }

    @Test
    void assignRoleFromInvitationRejectsADuplicateAssignment() {
        UUID roleId = UUID.randomUUID();
        when(assignmentRepository.findByTenantIdAndUserIdAndRoleId(tenantId, userId, roleId))
                .thenReturn(Optional.of(UserRoleAssignmentEntity.builder()
                        .tenantId(tenantId).userId(userId).roleId(roleId).build()));

        assertThatThrownBy(() -> service.assignRoleFromInvitation(tenantId, userId, roleId))
                .isInstanceOf(GenAdmConflictException.class);
    }
}
