package com.example.admsvc.infrastructure.persistence;

import com.example.admsvc.domain.enums.SupportTicketPriority;
import com.example.admsvc.domain.enums.SupportTicketStatus;
import com.example.admsvc.domain.enums.SupportTicketType;
import com.example.admsvc.infrastructure.persistence.entity.SupportTicketEntity;
import com.example.admsvc.infrastructure.persistence.repository.SupportTicketRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(classes = SupportTicketPersistenceIntegrationTest.TestApp.class)
class SupportTicketPersistenceIntegrationTest {

    @SpringBootApplication
    static class TestApp {
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private SupportTicketRepository repository;

    private SupportTicketEntity newTicket(UUID tenantId, UUID requestedBy) {
        return SupportTicketEntity.builder()
                .tenantId(tenantId)
                .requestedByUserId(requestedBy)
                .type(SupportTicketType.TECHNICAL)
                .subject("Unable to access dashboard")
                .description("Users are receiving 500 errors while opening dashboard pages.")
                .priority(SupportTicketPriority.HIGH)
                .build();
    }

    @Test
    void savesAndFindsByIdAndTenantDefaultingToOpen() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        SupportTicketEntity saved = repository.saveAndFlush(newTicket(tenantId, requestedBy));

        Optional<SupportTicketEntity> found = repository.findByIdAndTenantId(saved.getId(), tenantId);
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(SupportTicketStatus.OPEN);
        assertThat(found.get().getResolvedByUserId()).isNull();
        assertThat(found.get().getClosedByUserId()).isNull();
    }

    @Test
    void aTicketIsInvisibleWhenLookedUpUnderTheWrongTenant() {
        UUID tenantId = UUID.randomUUID();
        SupportTicketEntity saved = repository.saveAndFlush(newTicket(tenantId, UUID.randomUUID()));

        assertThat(repository.findByIdAndTenantId(saved.getId(), UUID.randomUUID())).isEmpty();
    }

    @Test
    void findsAllTicketsForATenantRegardlessOfRequester() {
        UUID tenantId = UUID.randomUUID();
        SupportTicketEntity a = repository.saveAndFlush(newTicket(tenantId, UUID.randomUUID()));
        SupportTicketEntity b = repository.saveAndFlush(newTicket(tenantId, UUID.randomUUID()));
        repository.saveAndFlush(newTicket(UUID.randomUUID(), UUID.randomUUID())); // different tenant

        List<SupportTicketEntity> all = repository.findAllByTenantId(tenantId);
        assertThat(all).extracting(SupportTicketEntity::getId).containsExactlyInAnyOrder(a.getId(), b.getId());
    }

    @Test
    void findsOnlyTheRequestersOwnTicketsForListMine() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        SupportTicketEntity mine = repository.saveAndFlush(newTicket(tenantId, requestedBy));
        repository.saveAndFlush(newTicket(tenantId, UUID.randomUUID())); // someone else's, same tenant

        List<SupportTicketEntity> mineOnly = repository.findAllByTenantIdAndRequestedByUserId(tenantId, requestedBy);
        assertThat(mineOnly).extracting(SupportTicketEntity::getId).containsExactly(mine.getId());
    }
}
