package com.company.bsmsvc.infrastructure.persistence.repository;

import com.company.bsmsvc.infrastructure.persistence.entity.PpmChangeSnapshotEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PpmChangeSnapshotJpaRepository
    extends JpaRepository<PpmChangeSnapshotEntity, UUID> {

    List<PpmChangeSnapshotEntity> findBySubscription_IdOrderByChangedAtDesc(UUID subscriptionId);
}
