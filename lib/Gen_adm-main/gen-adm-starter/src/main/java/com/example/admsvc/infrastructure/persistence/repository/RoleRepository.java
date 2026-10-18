package com.example.admsvc.infrastructure.persistence.repository;

import com.example.admsvc.infrastructure.persistence.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<RoleEntity, UUID> {

    Optional<RoleEntity> findByTenantIdAndName(UUID tenantId, String name);

    List<RoleEntity> findAllByTenantId(UUID tenantId);

    boolean existsByTenantId(UUID tenantId);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);
}
