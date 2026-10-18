package com.example.admsvc.domain.port;

import com.example.admsvc.domain.enums.SupportTicketPriority;
import com.example.admsvc.domain.enums.SupportTicketType;

import java.util.UUID;

/**
 * Optional synchronous hook for a consumer to react to a new support ticket
 * (e.g. forward it into a real support tool — Zendesk, Jira, etc.) without
 * Gen_ADM owning that integration itself. Not an outbox — no persistence,
 * no retry of the notification itself. Defaults to a no-op
 * ({@link com.example.admsvc.infrastructure.event.NoOpTicketEventPublisher}).
 */
public interface TicketEventPublisher {

    void onCreated(UUID tenantId, UUID ticketId, SupportTicketType type, SupportTicketPriority priority);
}
