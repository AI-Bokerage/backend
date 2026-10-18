package com.example.admsvc.infrastructure.persistence.repository;

import com.example.admsvc.infrastructure.persistence.entity.DataExportEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DataExportRepository extends JpaRepository<DataExportEntity, UUID> {

    Optional<DataExportEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<DataExportEntity> findAllByTenantId(UUID tenantId);
}
