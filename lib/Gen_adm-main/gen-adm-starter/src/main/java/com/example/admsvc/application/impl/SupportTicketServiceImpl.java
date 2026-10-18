package com.example.admsvc.application.impl;

import com.example.admsvc.application.service.SupportTicketService;
import com.example.admsvc.common.exception.GenAdmConflictException;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.domain.enums.SupportTicketPriority;
import com.example.admsvc.domain.enums.SupportTicketStatus;
import com.example.admsvc.domain.enums.SupportTicketType;
import com.example.admsvc.domain.port.TicketEventPublisher;
import com.example.admsvc.infrastructure.persistence.entity.SupportTicketEntity;
import com.example.admsvc.infrastructure.persistence.repository.SupportTicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SupportTicketServiceImpl implements SupportTicketService {

    private final SupportTicketRepository repository;
    private final TicketEventPublisher eventPublisher;

    public SupportTicketServiceImpl(SupportTicketRepository repository, TicketEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public SupportTicketEntity create(UUID tenantId, UUID requestedByUserId, SupportTicketType type,
                                       String subject, String description, SupportTicketPriority priority) {
        SupportTicketEntity saved = repository.saveAndFlush(SupportTicketEntity.builder()
                .tenantId(tenantId)
                .requestedByUserId(requestedByUserId)
                .type(type)
                .subject(subject)
                .description(description)
                .priority(priority)
                .build());
        eventPublisher.onCreated(tenantId, saved.getId(), type, priority);
        return saved;
    }

    @Override
    @Transactional
    public List<SupportTicketEntity> listMine(UUID tenantId, UUID requestedByUserId) {
        return repository.findAllByTenantIdAndRequestedByUserId(tenantId, requestedByUserId);
    }

    @Override
    @Transactional
    public List<SupportTicketEntity> listAll(UUID tenantId) {
        return repository.findAllByTenantId(tenantId);
    }

    @Override
    @Transactional
    public SupportTicketEntity start(UUID tenantId, UUID ticketId) {
        SupportTicketEntity ticket = findOrThrow(tenantId, ticketId);
        if (ticket.getStatus() != SupportTicketStatus.OPEN) {
            throw new GenAdmConflictException("Ticket is not open: " + ticketId);
        }
        ticket.setStatus(SupportTicketStatus.IN_PROGRESS);
        ticket.setStartedAt(Instant.now());
        return repository.saveAndFlush(ticket);
    }

    @Override
    @Transactional
    public SupportTicketEntity resolve(UUID tenantId, UUID ticketId, UUID resolvedByUserId) {
        SupportTicketEntity ticket = findOrThrow(tenantId, ticketId);
        if (ticket.getStatus() != SupportTicketStatus.IN_PROGRESS) {
            throw new GenAdmConflictException("Ticket is not in progress: " + ticketId);
        }
        ticket.setStatus(SupportTicketStatus.RESOLVED);
        ticket.setResolvedAt(Instant.now());
        ticket.setResolvedByUserId(resolvedByUserId);
        return repository.saveAndFlush(ticket);
    }

    @Override
    @Transactional
    public SupportTicketEntity close(UUID tenantId, UUID ticketId, UUID closedByUserId) {
        SupportTicketEntity ticket = findOrThrow(tenantId, ticketId);
        if (ticket.getStatus() == SupportTicketStatus.CLOSED) {
            throw new GenAdmConflictException("Ticket is already closed: " + ticketId);
        }
        ticket.setStatus(SupportTicketStatus.CLOSED);
        ticket.setClosedAt(Instant.now());
        ticket.setClosedByUserId(closedByUserId);
        return repository.saveAndFlush(ticket);
    }

    private SupportTicketEntity findOrThrow(UUID tenantId, UUID ticketId) {
        return repository.findByIdAndTenantId(ticketId, tenantId)
                .orElseThrow(() -> new GenAdmNotFoundException("Support ticket not found: " + ticketId));
    }
}
