package com.company.bsmsvc.infrastructure.persistence.repository;

import com.company.bsmsvc.infrastructure.persistence.entity.TenantBillingProfileEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantBillingProfileJpaRepository extends JpaRepository<TenantBillingProfileEntity, UUID> {
    Optional<TenantBillingProfileEntity> findByTenantId(UUID tenantId);
}
