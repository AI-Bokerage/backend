package com.example.admsvc.infrastructure.persistence.repository;

import com.example.admsvc.infrastructure.persistence.entity.SupportTicketEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupportTicketRepository extends JpaRepository<SupportTicketEntity, UUID> {

    Optional<SupportTicketEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<SupportTicketEntity> findAllByTenantId(UUID tenantId);

    List<SupportTicketEntity> findAllByTenantIdAndRequestedByUserId(UUID tenantId, UUID requestedByUserId);
}
