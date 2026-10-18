package com.example.admsvc.infrastructure.security;

import com.example.admsvc.application.impl.ProbeService;
import com.example.admsvc.common.exception.GenAdmConfigException;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import com.example.admsvc.infrastructure.persistence.entity.RoleEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(classes = TenantIsolationIntegrationTest.TestApp.class)
class TenantIsolationIntegrationTest {

    @SpringBootApplication
    @ContextConfiguration
    static class TestApp {
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ProbeService probeService;

    @Autowired
    private EntityManager entityManager;

    record TestPrincipal(UUID tenantId, UUID userId) implements GenAdmPrincipal {
    }

    private void authenticateAs(UUID tenantId, UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(new TestPrincipal(tenantId, userId), null));
    }

    @Test
    void aTenantCannotSeeAnotherTenantsRoles() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        authenticateAs(tenantA, UUID.randomUUID());
        probeService.createRoleViaBootstrap(tenantA, "owner");

        authenticateAs(tenantB, UUID.randomUUID());
        probeService.createRoleViaBootstrap(tenantB, "owner");

        authenticateAs(tenantA, UUID.randomUUID());
        List<RoleEntity> visibleToA = probeService.listRolesForCurrentTenant();

        assertThat(visibleToA).hasSize(1);
        assertThat(visibleToA.get(0).getTenantId()).isEqualTo(tenantA);
    }

    @Test
    void missingPrincipalAndNoTenantIdParamThrowsConfigExceptionBeforeAnyQuery() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> probeService.listRolesForCurrentTenant())
                .isInstanceOf(GenAdmConfigException.class);
    }
}
