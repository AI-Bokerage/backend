package com.example.admsvc.application.service;

import com.example.admsvc.domain.enums.SupportTicketPriority;
import com.example.admsvc.domain.enums.SupportTicketType;
import com.example.admsvc.infrastructure.persistence.entity.SupportTicketEntity;

import java.util.List;
import java.util.UUID;

public interface SupportTicketService {

    SupportTicketEntity create(UUID tenantId, UUID requestedByUserId, SupportTicketType type,
                                String subject, String description, SupportTicketPriority priority);

    List<SupportTicketEntity> listMine(UUID tenantId, UUID requestedByUserId);

    List<SupportTicketEntity> listAll(UUID tenantId);

    SupportTicketEntity start(UUID tenantId, UUID ticketId);

    SupportTicketEntity resolve(UUID tenantId, UUID ticketId, UUID resolvedByUserId);

    SupportTicketEntity close(UUID tenantId, UUID ticketId, UUID closedByUserId);
}
