package com.example.admsvc.application.impl;

import com.example.admsvc.common.exception.GenAdmConflictException;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.infrastructure.persistence.entity.PermissionEntity;
import com.example.admsvc.infrastructure.persistence.entity.RoleEntity;
import com.example.admsvc.infrastructure.persistence.repository.PermissionRepository;
import com.example.admsvc.infrastructure.persistence.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleServiceImplTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @InjectMocks
    private RoleServiceImpl roleService;

    private final UUID tenantId = UUID.randomUUID();

    @Test
    void createsARole() {
        when(roleRepository.findByTenantIdAndName(tenantId, "owner")).thenReturn(Optional.empty());
        when(roleRepository.save(any(RoleEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RoleEntity created = roleService.createRole(tenantId, "owner", "Tenant owner");

        assertThat(created.getName()).isEqualTo("owner");
        assertThat(created.getTenantId()).isEqualTo(tenantId);
        verify(roleRepository).save(any(RoleEntity.class));
    }

    @Test
    void rejectsDuplicateRoleName() {
        when(roleRepository.findByTenantIdAndName(tenantId, "owner"))
                .thenReturn(Optional.of(RoleEntity.builder().tenantId(tenantId).name("owner").build()));

        assertThatThrownBy(() -> roleService.createRole(tenantId, "owner", "dup"))
                .isInstanceOf(GenAdmConflictException.class);
    }

    @Test
    void throwsNotFoundWhenRoleDoesNotExist() {
        UUID roleId = UUID.randomUUID();
        when(roleRepository.findById(roleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.getRole(tenantId, roleId))
                .isInstanceOf(GenAdmNotFoundException.class);
    }

    @Test
    void throwsNotFoundWhenRoleBelongsToAnotherTenant() {
        UUID roleId = UUID.randomUUID();
        UUID otherTenant = UUID.randomUUID();
        when(roleRepository.findById(roleId))
                .thenReturn(Optional.of(RoleEntity.builder().id(roleId).tenantId(otherTenant).name("x").build()));

        assertThatThrownBy(() -> roleService.getRole(tenantId, roleId))
                .isInstanceOf(GenAdmNotFoundException.class);
    }

    @Test
    void grantsPermissionsToARole() {
        UUID roleId = UUID.randomUUID();
        RoleEntity role = RoleEntity.builder().id(roleId).tenantId(tenantId).name("billing-admin").build();
        PermissionEntity permission = PermissionEntity.builder().code("billing:manage").build();

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(permissionRepository.findByCode("billing:manage")).thenReturn(Optional.of(permission));
        when(roleRepository.save(any(RoleEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RoleEntity updated = roleService.grantPermissions(tenantId, roleId, Set.of("billing:manage"));

        assertThat(updated.getPermissions()).extracting(PermissionEntity::getCode)
                .containsExactly("billing:manage");
    }
}
