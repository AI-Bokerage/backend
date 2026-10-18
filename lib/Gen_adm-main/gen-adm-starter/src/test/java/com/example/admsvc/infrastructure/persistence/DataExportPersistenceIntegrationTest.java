package com.example.admsvc.infrastructure.persistence;

import com.example.admsvc.domain.enums.DataExportStatus;
import com.example.admsvc.infrastructure.persistence.entity.DataExportEntity;
import com.example.admsvc.infrastructure.persistence.repository.DataExportRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(classes = DataExportPersistenceIntegrationTest.TestApp.class)
class DataExportPersistenceIntegrationTest {

    @SpringBootApplication
    static class TestApp {
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private DataExportRepository repository;

    private DataExportEntity newExport(UUID tenantId, UUID requestedBy) {
        return DataExportEntity.builder()
                .tenantId(tenantId)
                .requestedByUserId(requestedBy)
                .status(DataExportStatus.COMPLETED)
                .snapshotJson("{\"tenantId\":\"" + tenantId + "\"}")
                .recordCount(3L)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();
    }

    @Test
    void savesAndFindsByIdAndTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        DataExportEntity saved = repository.saveAndFlush(newExport(tenantId, requestedBy));

        Optional<DataExportEntity> found = repository.findByIdAndTenantId(saved.getId(), tenantId);
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(DataExportStatus.COMPLETED);
        assertThat(found.get().getSnapshotJson()).contains(tenantId.toString());
        assertThat(found.get().getRecordCount()).isEqualTo(3L);
        assertThat(found.get().getRevokedByUserId()).isNull();
    }

    @Test
    void anExportIsInvisibleWhenLookedUpUnderTheWrongTenant() {
        UUID tenantId = UUID.randomUUID();
        DataExportEntity saved = repository.saveAndFlush(newExport(tenantId, UUID.randomUUID()));

        assertThat(repository.findByIdAndTenantId(saved.getId(), UUID.randomUUID())).isEmpty();
    }

    @Test
    void findsAllExportsForATenantRegardlessOfRequester() {
        UUID tenantId = UUID.randomUUID();
        DataExportEntity a = repository.saveAndFlush(newExport(tenantId, UUID.randomUUID()));
        DataExportEntity b = repository.saveAndFlush(newExport(tenantId, UUID.randomUUID()));
        repository.saveAndFlush(newExport(UUID.randomUUID(), UUID.randomUUID())); // different tenant

        List<DataExportEntity> all = repository.findAllByTenantId(tenantId);
        assertThat(all).extracting(DataExportEntity::getId).containsExactlyInAnyOrder(a.getId(), b.getId());
    }
}
