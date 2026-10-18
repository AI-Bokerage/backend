package com.company.bsmsvc.infrastructure.persistence.repository;

import com.company.bsmsvc.domain.enums.DunningAttemptStatus;
import com.company.bsmsvc.infrastructure.persistence.entity.DunningAttemptEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DunningAttemptJpaRepository extends JpaRepository<DunningAttemptEntity, UUID> {
    List<DunningAttemptEntity> findBySubscriptionIdOrderByAttemptNumberAsc(UUID subscriptionId);
    Optional<DunningAttemptEntity> findTopBySubscriptionIdOrderByAttemptNumberDesc(UUID subscriptionId);
    List<DunningAttemptEntity> findByStatusAndNextRetryAtBefore(DunningAttemptStatus status, Instant now);
}
