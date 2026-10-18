package com.example.admsvc.infrastructure.persistence;

import com.example.admsvc.domain.enums.InvitationStatus;
import com.example.admsvc.infrastructure.persistence.entity.InvitationEntity;
import com.example.admsvc.infrastructure.persistence.repository.InvitationRepository;
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
@SpringBootTest(classes = InvitationPersistenceIntegrationTest.TestApp.class)
class InvitationPersistenceIntegrationTest {

    @SpringBootApplication
    static class TestApp {
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private InvitationRepository repository;

    private InvitationEntity newInvitation(UUID tenantId, String email) {
        InvitationEntity invitation = InvitationEntity.builder()
                .tenantId(tenantId)
                .email(email)
                .tokenHash(UUID.randomUUID().toString())
                .invitedByUserId(UUID.randomUUID())
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();
        invitation.setRoleIds(List.of(UUID.randomUUID()));
        return invitation;
    }

    @Test
    void savesAndFindsByTokenHashDefaultingToPending() {
        UUID tenantId = UUID.randomUUID();
        InvitationEntity saved = repository.saveAndFlush(newInvitation(tenantId, "a@example.com"));

        Optional<InvitationEntity> found = repository.findByTokenHash(saved.getTokenHash());
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(InvitationStatus.PENDING);
        assertThat(found.get().getRoleIds()).hasSize(1);
    }

    @Test
    void findByIdAndTenantIdExcludesTheWrongTenant() {
        UUID tenantId = UUID.randomUUID();
        InvitationEntity saved = repository.saveAndFlush(newInvitation(tenantId, "b@example.com"));

        assertThat(repository.findByIdAndTenantId(saved.getId(), tenantId)).isPresent();
        assertThat(repository.findByIdAndTenantId(saved.getId(), UUID.randomUUID())).isEmpty();
    }

    @Test
    void findsAllPendingInvitationsForATenant() {
        UUID tenantId = UUID.randomUUID();
        InvitationEntity pending = repository.saveAndFlush(newInvitation(tenantId, "c@example.com"));
        InvitationEntity cancelled = newInvitation(tenantId, "d@example.com");
        cancelled.setStatus(InvitationStatus.CANCELLED);
        repository.saveAndFlush(cancelled);
        repository.saveAndFlush(newInvitation(UUID.randomUUID(), "e@example.com")); // different tenant

        List<InvitationEntity> pendingOnly = repository.findAllByTenantIdAndStatus(tenantId, InvitationStatus.PENDING);
        assertThat(pendingOnly).extracting(InvitationEntity::getId).containsExactly(pending.getId());
    }

    @Test
    void findsAPendingInvitationByTenantAndEmailForTheDuplicateGuard() {
        UUID tenantId = UUID.randomUUID();
        InvitationEntity saved = repository.saveAndFlush(newInvitation(tenantId, "f@example.com"));

        Optional<InvitationEntity> found = repository.findByTenantIdAndEmailAndStatus(
                tenantId, "f@example.com", InvitationStatus.PENDING);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    void getRoleIdsReturnsAnEmptyListWhenRoleIdsJsonIsNull() {
        InvitationEntity invitation = new InvitationEntity();
        assertThat(invitation.getRoleIds()).isEmpty();
    }
}
