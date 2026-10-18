package com.example.admsvc.infrastructure.persistence;

import com.example.admsvc.domain.enums.ImpersonationSessionStatus;
import com.example.admsvc.infrastructure.persistence.entity.ImpersonationSessionEntity;
import com.example.admsvc.infrastructure.persistence.repository.ImpersonationSessionRepository;
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
@SpringBootTest(classes = ImpersonationPersistenceIntegrationTest.TestApp.class)
class ImpersonationPersistenceIntegrationTest {

    @SpringBootApplication
    static class TestApp {
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ImpersonationSessionRepository repository;

    private ImpersonationSessionEntity newSession(UUID tenantId) {
        return ImpersonationSessionEntity.builder()
                .tenantId(tenantId)
                .requestedByUserId(UUID.randomUUID())
                .targetUserId(UUID.randomUUID())
                .reason("support ticket #42")
                .expiresAt(Instant.now().plus(60, ChronoUnit.MINUTES))
                .build();
    }

    @Test
    void savesAndFindsASessionByIdAndTenantDefaultingToPendingConsent() {
        UUID tenantId = UUID.randomUUID();
        ImpersonationSessionEntity session = repository.saveAndFlush(newSession(tenantId));

        Optional<ImpersonationSessionEntity> found = repository.findByIdAndTenantId(session.getId(), tenantId);
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(ImpersonationSessionStatus.PENDING_CONSENT);
        assertThat(found.get().getReviewedByUserId()).isNull();
        assertThat(found.get().getEndedByUserId()).isNull();
    }

    @Test
    void aSessionIsInvisibleWhenLookedUpUnderTheWrongTenant() {
        UUID tenantId = UUID.randomUUID();
        ImpersonationSessionEntity session = repository.saveAndFlush(newSession(tenantId));

        Optional<ImpersonationSessionEntity> found = repository.findByIdAndTenantId(session.getId(), UUID.randomUUID());
        assertThat(found).isEmpty();
    }

    @Test
    void findsAllActionableSessionsForATenantFilteredByStatus() {
        UUID tenantId = UUID.randomUUID();
        ImpersonationSessionEntity pending = repository.saveAndFlush(newSession(tenantId));
        ImpersonationSessionEntity denied = newSession(tenantId);
        denied.setStatus(ImpersonationSessionStatus.DENIED);
        repository.saveAndFlush(denied);
        repository.saveAndFlush(newSession(UUID.randomUUID())); // different tenant, must not appear

        List<ImpersonationSessionEntity> actionable = repository.findAllByTenantIdAndStatusIn(
                tenantId, List.of(ImpersonationSessionStatus.PENDING_CONSENT, ImpersonationSessionStatus.ACTIVE));

        assertThat(actionable).extracting(ImpersonationSessionEntity::getId).containsExactly(pending.getId());
    }
}
