package com.example.admsvc.application.impl;

import com.example.admsvc.common.exception.GenAdmConflictException;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.domain.enums.DataExportStatus;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import com.example.admsvc.infrastructure.persistence.entity.DataExportEntity;
import com.example.admsvc.infrastructure.persistence.entity.RoleEntity;
import com.example.admsvc.infrastructure.persistence.repository.DataExportRepository;
import com.example.admsvc.infrastructure.persistence.repository.RoleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(classes = DataExportIntegrationTest.TestApp.class)
class DataExportIntegrationTest {

    @SpringBootApplication
    static class TestApp {
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    record TestPrincipal(UUID tenantId, UUID userId) implements GenAdmPrincipal {
    }

    @Autowired
    private DataExportServiceImpl dataExportService;

    @Autowired
    private DataExportRepository dataExportRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private void authenticateAs(UUID tenantId, UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(new TestPrincipal(tenantId, userId), null));
    }

    @Test
    void requestDownloadRevokeFlow() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        UUID revokedBy = UUID.randomUUID();
        authenticateAs(tenantId, requestedBy);
        roleRepository.saveAndFlush(RoleEntity.builder().tenantId(tenantId).name("owner").build());

        DataExportEntity created = dataExportService.request(tenantId, requestedBy);
        assertThat(created.getStatus()).isEqualTo(DataExportStatus.COMPLETED);
        assertThat(created.getRecordCount()).isGreaterThanOrEqualTo(1L);

        TenantExportSnapshot snapshot = objectMapper.readValue(created.getSnapshotJson(), TenantExportSnapshot.class);
        assertThat(snapshot.roles()).hasSizeGreaterThanOrEqualTo(1);

        String downloaded = dataExportService.download(tenantId, created.getId());
        assertThat(downloaded).isEqualTo(created.getSnapshotJson());

        DataExportEntity revoked = dataExportService.revoke(tenantId, created.getId(), revokedBy);
        assertThat(revoked.getStatus()).isEqualTo(DataExportStatus.REVOKED);
        assertThat(revoked.getRevokedByUserId()).isEqualTo(revokedBy);

        assertThatThrownBy(() -> dataExportService.download(tenantId, created.getId()))
                .isInstanceOf(GenAdmConflictException.class);
    }

    @Test
    void listExportsReturnsAllExportsForTheTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        authenticateAs(tenantId, requestedBy);

        dataExportService.request(tenantId, requestedBy);
        dataExportService.request(tenantId, requestedBy);

        List<DataExportEntity> all = dataExportService.listExports(tenantId);
        assertThat(all).hasSize(2);
    }

    @Test
    void crossTenantLookupIs404() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        authenticateAs(tenantId, requestedBy);
        DataExportEntity created = dataExportService.request(tenantId, requestedBy);

        authenticateAs(UUID.randomUUID(), UUID.randomUUID());
        assertThatThrownBy(() -> dataExportService.getStatus(UUID.randomUUID(), created.getId()))
                .isInstanceOf(GenAdmNotFoundException.class);
    }

    @Test
    void downloadingAfterTtlElapsedLazilyExpiresAndReturnsConflict() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        authenticateAs(tenantId, requestedBy);
        DataExportEntity created = dataExportService.request(tenantId, requestedBy);

        DataExportEntity stored = dataExportRepository.findById(created.getId()).orElseThrow();
        stored.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        dataExportRepository.saveAndFlush(stored);

        assertThatThrownBy(() -> dataExportService.download(tenantId, created.getId()))
                .isInstanceOf(GenAdmConflictException.class);

        DataExportEntity afterExpiry = dataExportRepository.findById(created.getId()).orElseThrow();
        assertThat(afterExpiry.getStatus()).isEqualTo(DataExportStatus.EXPIRED);
        assertThat(afterExpiry.getSnapshotJson()).isNull();
    }
}
