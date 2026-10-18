package com.example.admsvc.application.impl;

import com.example.admsvc.common.exception.GenAdmConflictException;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.domain.enums.SupportTicketPriority;
import com.example.admsvc.domain.enums.SupportTicketStatus;
import com.example.admsvc.domain.enums.SupportTicketType;
import com.example.admsvc.domain.port.TicketEventPublisher;
import com.example.admsvc.infrastructure.persistence.entity.SupportTicketEntity;
import com.example.admsvc.infrastructure.persistence.repository.SupportTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupportTicketServiceImplTest {

    @Mock
    private SupportTicketRepository repository;

    @Mock
    private TicketEventPublisher eventPublisher;

    private SupportTicketServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SupportTicketServiceImpl(repository, eventPublisher);
        lenient().when(repository.saveAndFlush(any())).thenAnswer(inv -> {
            SupportTicketEntity ticket = inv.getArgument(0);
            if (ticket.getId() == null) {
                ticket.setId(UUID.randomUUID());
            }
            if (ticket.getStatus() == null) {
                ticket.setStatus(SupportTicketStatus.OPEN);
            }
            return ticket;
        });
    }

    private SupportTicketEntity ticket(UUID tenantId, SupportTicketStatus status) {
        return SupportTicketEntity.builder()
                .id(UUID.randomUUID()).tenantId(tenantId)
                .requestedByUserId(UUID.randomUUID())
                .type(SupportTicketType.TECHNICAL)
                .subject("s").description("d")
                .priority(SupportTicketPriority.HIGH)
                .status(status)
                .build();
    }

    @Test
    void createPersistsAnOpenTicketAndFiresOnCreated() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();

        SupportTicketEntity ticket = service.create(tenantId, requestedBy, SupportTicketType.BILLING,
                "Invoice question", "Why was I charged twice?", SupportTicketPriority.LOW);

        assertThat(ticket.getStatus()).isEqualTo(SupportTicketStatus.OPEN);
        assertThat(ticket.getTenantId()).isEqualTo(tenantId);
        assertThat(ticket.getRequestedByUserId()).isEqualTo(requestedBy);
        verify(eventPublisher).onCreated(eq(tenantId), any(), eq(SupportTicketType.BILLING), eq(SupportTicketPriority.LOW));
    }

    @Test
    void listMineDelegatesToTheRequesterScopedQuery() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        SupportTicketEntity mine = ticket(tenantId, SupportTicketStatus.OPEN);
        when(repository.findAllByTenantIdAndRequestedByUserId(tenantId, requestedBy)).thenReturn(List.of(mine));

        List<SupportTicketEntity> result = service.listMine(tenantId, requestedBy);

        assertThat(result).containsExactly(mine);
    }

    @Test
    void listAllDelegatesToTheTenantScopedQuery() {
        UUID tenantId = UUID.randomUUID();
        SupportTicketEntity a = ticket(tenantId, SupportTicketStatus.OPEN);
        when(repository.findAllByTenantId(tenantId)).thenReturn(List.of(a));

        assertThat(service.listAll(tenantId)).containsExactly(a);
    }

    @Test
    void startFlipsAnOpenTicketToInProgress() {
        UUID tenantId = UUID.randomUUID();
        SupportTicketEntity ticket = ticket(tenantId, SupportTicketStatus.OPEN);
        when(repository.findByIdAndTenantId(ticket.getId(), tenantId)).thenReturn(Optional.of(ticket));

        SupportTicketEntity started = service.start(tenantId, ticket.getId());

        assertThat(started.getStatus()).isEqualTo(SupportTicketStatus.IN_PROGRESS);
        assertThat(started.getStartedAt()).isNotNull();
    }

    @Test
    void startThrowsConflictWhenNotOpen() {
        UUID tenantId = UUID.randomUUID();
        SupportTicketEntity ticket = ticket(tenantId, SupportTicketStatus.IN_PROGRESS);
        when(repository.findByIdAndTenantId(ticket.getId(), tenantId)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.start(tenantId, ticket.getId()))
                .isInstanceOf(GenAdmConflictException.class);
    }

    @Test
    void resolveFlipsAnInProgressTicketToResolved() {
        UUID tenantId = UUID.randomUUID();
        UUID resolvedBy = UUID.randomUUID();
        SupportTicketEntity ticket = ticket(tenantId, SupportTicketStatus.IN_PROGRESS);
        when(repository.findByIdAndTenantId(ticket.getId(), tenantId)).thenReturn(Optional.of(ticket));

        SupportTicketEntity resolved = service.resolve(tenantId, ticket.getId(), resolvedBy);

        assertThat(resolved.getStatus()).isEqualTo(SupportTicketStatus.RESOLVED);
        assertThat(resolved.getResolvedByUserId()).isEqualTo(resolvedBy);
        assertThat(resolved.getResolvedAt()).isNotNull();
    }

    @Test
    void resolveThrowsConflictWhenNotInProgress() {
        UUID tenantId = UUID.randomUUID();
        SupportTicketEntity ticket = ticket(tenantId, SupportTicketStatus.OPEN);
        when(repository.findByIdAndTenantId(ticket.getId(), tenantId)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.resolve(tenantId, ticket.getId(), UUID.randomUUID()))
                .isInstanceOf(GenAdmConflictException.class);
    }

    @Test
    void closeIsAllowedFromOpenWithoutGoingThroughInProgressOrResolved() {
        UUID tenantId = UUID.randomUUID();
        UUID closedBy = UUID.randomUUID();
        SupportTicketEntity ticket = ticket(tenantId, SupportTicketStatus.OPEN);
        when(repository.findByIdAndTenantId(ticket.getId(), tenantId)).thenReturn(Optional.of(ticket));

        SupportTicketEntity closed = service.close(tenantId, ticket.getId(), closedBy);

        assertThat(closed.getStatus()).isEqualTo(SupportTicketStatus.CLOSED);
        assertThat(closed.getClosedByUserId()).isEqualTo(closedBy);
    }

    @Test
    void closeIsAllowedFromInProgress() {
        UUID tenantId = UUID.randomUUID();
        SupportTicketEntity ticket = ticket(tenantId, SupportTicketStatus.IN_PROGRESS);
        when(repository.findByIdAndTenantId(ticket.getId(), tenantId)).thenReturn(Optional.of(ticket));

        assertThat(service.close(tenantId, ticket.getId(), UUID.randomUUID()).getStatus())
                .isEqualTo(SupportTicketStatus.CLOSED);
    }

    @Test
    void closeIsAllowedFromResolved() {
        UUID tenantId = UUID.randomUUID();
        SupportTicketEntity ticket = ticket(tenantId, SupportTicketStatus.RESOLVED);
        when(repository.findByIdAndTenantId(ticket.getId(), tenantId)).thenReturn(Optional.of(ticket));

        assertThat(service.close(tenantId, ticket.getId(), UUID.randomUUID()).getStatus())
                .isEqualTo(SupportTicketStatus.CLOSED);
    }

    @Test
    void closeThrowsConflictWhenAlreadyClosed() {
        UUID tenantId = UUID.randomUUID();
        SupportTicketEntity ticket = ticket(tenantId, SupportTicketStatus.CLOSED);
        when(repository.findByIdAndTenantId(ticket.getId(), tenantId)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.close(tenantId, ticket.getId(), UUID.randomUUID()))
                .isInstanceOf(GenAdmConflictException.class);
    }

    @Test
    void throwsNotFoundForACrossTenantLookup() {
        UUID ticketId = UUID.randomUUID();
        when(repository.findByIdAndTenantId(eq(ticketId), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.start(UUID.randomUUID(), ticketId))
                .isInstanceOf(GenAdmNotFoundException.class);
    }
}
