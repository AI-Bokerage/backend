package com.example.admsvc.infrastructure.persistence.repository;

import com.example.admsvc.infrastructure.persistence.entity.OffboardingJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OffboardingJobRepository extends JpaRepository<OffboardingJobEntity, UUID> {

    Optional<OffboardingJobEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<OffboardingJobEntity> findAllByTenantId(UUID tenantId);
}
