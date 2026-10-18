package com.example.admsvc.api.dto.response;

import com.example.admsvc.infrastructure.persistence.entity.SupportTicketEntity;

import java.time.Instant;
import java.util.UUID;

public record SupportTicketResponse(
        UUID id,
        UUID tenantId,
        UUID requestedByUserId,
        String type,
        String subject,
        String description,
        String priority,
        String status,
        Instant startedAt,
        Instant resolvedAt,
        UUID resolvedByUserId,
        Instant closedAt,
        UUID closedByUserId,
        Instant createdAt) {

    public static SupportTicketResponse from(SupportTicketEntity ticket) {
        return new SupportTicketResponse(
                ticket.getId(),
                ticket.getTenantId(),
                ticket.getRequestedByUserId(),
                ticket.getType().name(),
                ticket.getSubject(),
                ticket.getDescription(),
                ticket.getPriority().name(),
                ticket.getStatus().name(),
                ticket.getStartedAt(),
                ticket.getResolvedAt(),
                ticket.getResolvedByUserId(),
                ticket.getClosedAt(),
                ticket.getClosedByUserId(),
                ticket.getCreatedAt());
    }
}
