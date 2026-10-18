package com.example.admsvc.infrastructure.persistence.repository;

import com.example.admsvc.domain.enums.InvitationStatus;
import com.example.admsvc.infrastructure.persistence.entity.InvitationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvitationRepository extends JpaRepository<InvitationEntity, UUID> {

    Optional<InvitationEntity> findByTokenHash(String tokenHash);

    Optional<InvitationEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<InvitationEntity> findAllByTenantIdAndStatus(UUID tenantId, InvitationStatus status);

    Optional<InvitationEntity> findByTenantIdAndEmailAndStatus(UUID tenantId, String email, InvitationStatus status);

    List<InvitationEntity> findAllByTenantId(UUID tenantId);
}
