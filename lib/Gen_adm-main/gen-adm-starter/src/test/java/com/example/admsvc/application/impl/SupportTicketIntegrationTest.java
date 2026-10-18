package com.example.admsvc.application.impl;

import com.example.admsvc.common.exception.GenAdmConflictException;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.domain.enums.SupportTicketPriority;
import com.example.admsvc.domain.enums.SupportTicketStatus;
import com.example.admsvc.domain.enums.SupportTicketType;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import com.example.admsvc.domain.port.TicketEventPublisher;
import com.example.admsvc.infrastructure.persistence.entity.SupportTicketEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(classes = SupportTicketIntegrationTest.TestApp.class)
class SupportTicketIntegrationTest {

    @SpringBootApplication
    static class TestApp {

        @Bean
        RecordingTicketEventPublisher recordingTicketEventPublisher() {
            return new RecordingTicketEventPublisher();
        }
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static class RecordingTicketEventPublisher implements TicketEventPublisher {
        final List<String> events = new ArrayList<>();

        @Override
        public void onCreated(UUID tenantId, UUID ticketId, SupportTicketType type, SupportTicketPriority priority) {
            events.add("CREATED:" + type);
        }
    }

    record TestPrincipal(UUID tenantId, UUID userId) implements GenAdmPrincipal {
    }

    @Autowired
    private SupportTicketServiceImpl supportTicketService;

    @Autowired
    private RecordingTicketEventPublisher eventPublisher;

    @BeforeEach
    void resetRecordedEvents() {
        eventPublisher.events.clear();
    }

    private void authenticateAs(UUID tenantId, UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(new TestPrincipal(tenantId, userId), null));
    }

    @Test
    void createStartResolveCloseFlow() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        authenticateAs(tenantId, requestedBy);

        SupportTicketEntity created = supportTicketService.create(tenantId, requestedBy,
                SupportTicketType.TECHNICAL, "Dashboard down", "500 errors on load", SupportTicketPriority.HIGH);
        assertThat(created.getStatus()).isEqualTo(SupportTicketStatus.OPEN);
        assertThat(eventPublisher.events).containsExactly("CREATED:TECHNICAL");

        SupportTicketEntity started = supportTicketService.start(tenantId, created.getId());
        assertThat(started.getStatus()).isEqualTo(SupportTicketStatus.IN_PROGRESS);

        SupportTicketEntity resolved = supportTicketService.resolve(tenantId, created.getId(), admin);
        assertThat(resolved.getStatus()).isEqualTo(SupportTicketStatus.RESOLVED);
        assertThat(resolved.getResolvedByUserId()).isEqualTo(admin);

        SupportTicketEntity closed = supportTicketService.close(tenantId, created.getId(), admin);
        assertThat(closed.getStatus()).isEqualTo(SupportTicketStatus.CLOSED);
        assertThat(closed.getClosedByUserId()).isEqualTo(admin);
    }

    @Test
    void closeIsAllowedDirectlyFromOpenSkippingInProgressAndResolved() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        authenticateAs(tenantId, requestedBy);

        SupportTicketEntity created = supportTicketService.create(tenantId, requestedBy,
                SupportTicketType.GENERAL, "Duplicate ticket", "Filed by mistake", SupportTicketPriority.LOW);

        SupportTicketEntity closed = supportTicketService.close(tenantId, created.getId(), admin);

        assertThat(closed.getStatus()).isEqualTo(SupportTicketStatus.CLOSED);
    }

    @Test
    void listMineOnlyReturnsTheRequestersOwnTickets() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        authenticateAs(tenantId, requestedBy);
        SupportTicketEntity mine = supportTicketService.create(tenantId, requestedBy,
                SupportTicketType.ACCOUNT, "s1", "d1", SupportTicketPriority.MEDIUM);

        authenticateAs(tenantId, UUID.randomUUID());
        supportTicketService.create(tenantId, UUID.randomUUID(),
                SupportTicketType.ACCOUNT, "s2", "d2", SupportTicketPriority.MEDIUM);

        authenticateAs(tenantId, requestedBy);
        List<SupportTicketEntity> mineList = supportTicketService.listMine(tenantId, requestedBy);

        assertThat(mineList).extracting(SupportTicketEntity::getId).containsExactly(mine.getId());
    }

    @Test
    void crossTenantLookupIs404() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        authenticateAs(tenantId, requestedBy);
        SupportTicketEntity created = supportTicketService.create(tenantId, requestedBy,
                SupportTicketType.OTHER, "s", "d", SupportTicketPriority.URGENT);

        authenticateAs(UUID.randomUUID(), UUID.randomUUID());
        assertThatThrownBy(() -> supportTicketService.start(UUID.randomUUID(), created.getId()))
                .isInstanceOf(GenAdmNotFoundException.class);
    }

    @Test
    void resolvingAnOpenTicketWithoutStartingItIsAConflict() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        authenticateAs(tenantId, requestedBy);
        SupportTicketEntity created = supportTicketService.create(tenantId, requestedBy,
                SupportTicketType.TECHNICAL, "s", "d", SupportTicketPriority.HIGH);

        assertThatThrownBy(() -> supportTicketService.resolve(tenantId, created.getId(), UUID.randomUUID()))
                .isInstanceOf(GenAdmConflictException.class);
    }
}
