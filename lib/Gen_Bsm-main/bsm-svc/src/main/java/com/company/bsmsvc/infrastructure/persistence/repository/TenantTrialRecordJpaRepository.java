package com.company.bsmsvc.infrastructure.persistence.repository;

import com.company.bsmsvc.infrastructure.persistence.entity.TenantTrialRecordEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantTrialRecordJpaRepository extends JpaRepository<TenantTrialRecordEntity, UUID> {

    boolean existsByTenantId(UUID tenantId);
}
