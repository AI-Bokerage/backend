package com.example.admsvc.infrastructure.event;

import com.example.admsvc.domain.enums.SupportTicketPriority;
import com.example.admsvc.domain.enums.SupportTicketType;
import com.example.admsvc.domain.port.TicketEventPublisher;

import java.util.UUID;

public class NoOpTicketEventPublisher implements TicketEventPublisher {

    @Override
    public void onCreated(UUID tenantId, UUID ticketId, SupportTicketType type, SupportTicketPriority priority) {
    }
}
