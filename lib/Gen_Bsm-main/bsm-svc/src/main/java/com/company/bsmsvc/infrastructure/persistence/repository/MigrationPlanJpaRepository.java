package com.company.bsmsvc.infrastructure.persistence.repository;

import com.company.bsmsvc.infrastructure.persistence.entity.MigrationPlanEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MigrationPlanJpaRepository
    extends JpaRepository<MigrationPlanEntity, UUID>, JpaSpecificationExecutor<MigrationPlanEntity> {
}
