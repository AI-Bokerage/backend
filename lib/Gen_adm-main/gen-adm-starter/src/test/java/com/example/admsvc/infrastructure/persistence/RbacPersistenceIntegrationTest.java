package com.example.admsvc.infrastructure.persistence;

import com.example.admsvc.config.GenAdmAutoConfiguration;
import com.example.admsvc.infrastructure.persistence.entity.PermissionEntity;
import com.example.admsvc.infrastructure.persistence.entity.RoleEntity;
import com.example.admsvc.infrastructure.persistence.entity.UserRoleAssignmentEntity;
import com.example.admsvc.infrastructure.persistence.repository.PermissionRepository;
import com.example.admsvc.infrastructure.persistence.repository.RoleRepository;
import com.example.admsvc.infrastructure.persistence.repository.UserRoleAssignmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(classes = RbacPersistenceIntegrationTest.TestApp.class)
class RbacPersistenceIntegrationTest {

    @SpringBootApplication
    static class TestApp {
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private UserRoleAssignmentRepository assignmentRepository;

    @Test
    void savesAndFindsARoleByTenantAndName() {
        UUID tenantId = UUID.randomUUID();
        RoleEntity role = RoleEntity.builder()
                .tenantId(tenantId)
                .name("owner")
                .description("Tenant owner")
                .systemRole(false)
                .build();
        roleRepository.saveAndFlush(role);

        Optional<RoleEntity> found = roleRepository.findByTenantIdAndName(tenantId, "owner");
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(role.getId());
    }

    @Test
    void rejectsDuplicateRoleNameWithinATenant() {
        UUID tenantId = UUID.randomUUID();
        roleRepository.saveAndFlush(RoleEntity.builder().tenantId(tenantId).name("owner").build());

        assertThatThrownBy(() ->
                roleRepository.saveAndFlush(RoleEntity.builder().tenantId(tenantId).name("owner").build())
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void savesAndFindsAPermissionByCode() {
        PermissionEntity permission = PermissionEntity.builder()
                .code("users:read")
                .description("Read users")
                .build();
        permissionRepository.saveAndFlush(permission);

        Optional<PermissionEntity> found = permissionRepository.findByCode("users:read");
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(permission.getId());
    }

    @Test
    void grantsAPermissionToARoleViaTheJoinTable() {
        UUID tenantId = UUID.randomUUID();
        PermissionEntity permission = permissionRepository.saveAndFlush(
                PermissionEntity.builder().code("billing:manage").build());
        RoleEntity role = RoleEntity.builder().tenantId(tenantId).name("billing-admin").build();
        role.getPermissions().add(permission);
        roleRepository.saveAndFlush(role);

        RoleEntity reloaded = roleRepository.findById(role.getId()).orElseThrow();
        assertThat(reloaded.getPermissions()).extracting(PermissionEntity::getCode)
                .containsExactly("billing:manage");
    }

    @Test
    void assignsARoleToAUserAndListsItByTenantAndUser() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        RoleEntity role = roleRepository.saveAndFlush(
                RoleEntity.builder().tenantId(tenantId).name("member").build());

        UserRoleAssignmentEntity assignment = UserRoleAssignmentEntity.builder()
                .tenantId(tenantId)
                .userId(userId)
                .roleId(role.getId())
                .build();
        assignmentRepository.saveAndFlush(assignment);

        List<UserRoleAssignmentEntity> found = assignmentRepository.findAllByTenantIdAndUserId(tenantId, userId);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getRoleId()).isEqualTo(role.getId());
    }
}
