package com.company.bsmsvc.infrastructure.persistence.repository;

import com.company.bsmsvc.infrastructure.persistence.entity.MigrationPlanItemEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MigrationPlanItemJpaRepository extends JpaRepository<MigrationPlanItemEntity, UUID> {

    List<MigrationPlanItemEntity> findAllByMigrationPlanId(UUID migrationPlanId);
}
