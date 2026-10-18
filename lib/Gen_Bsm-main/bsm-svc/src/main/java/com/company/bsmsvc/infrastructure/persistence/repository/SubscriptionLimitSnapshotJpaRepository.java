package com.company.bsmsvc.infrastructure.persistence.repository;

import com.company.bsmsvc.infrastructure.persistence.entity.SubscriptionLimitSnapshotEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SubscriptionLimitSnapshotJpaRepository
    extends JpaRepository<SubscriptionLimitSnapshotEntity, UUID>, JpaSpecificationExecutor<SubscriptionLimitSnapshotEntity> {
}
