package com.example.admsvc.infrastructure.persistence.repository;

import com.example.admsvc.domain.enums.ImpersonationSessionStatus;
import com.example.admsvc.infrastructure.persistence.entity.ImpersonationSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImpersonationSessionRepository extends JpaRepository<ImpersonationSessionEntity, UUID> {

    Optional<ImpersonationSessionEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<ImpersonationSessionEntity> findAllByTenantIdAndStatusIn(UUID tenantId, List<ImpersonationSessionStatus> statuses);

    List<ImpersonationSessionEntity> findAllByTenantId(UUID tenantId);
}
