package com.company.bsmsvc.infrastructure.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface BsmOutboxJpaRepository extends JpaRepository<BsmOutboxEventEntity, UUID> {

    List<BsmOutboxEventEntity> findByStatusOrderByCreatedAtAsc(BsmOutboxEventStatus status);

    /**
     * Selects up to {@code limit} PENDING outbox rows and locks them with
     * {@code FOR UPDATE SKIP LOCKED}.  Rows already locked by another transaction
     * (i.e. another pod processing the same batch) are silently skipped, so each
     * pod receives a disjoint set of rows to dispatch.  This prevents duplicate
     * RabbitMQ message delivery under multi-pod deployment.
     */
    @Query(
        value = "SELECT * FROM bsm_outbox_events WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED",
        nativeQuery = true
    )
    List<BsmOutboxEventEntity> findPendingSkipLocked(@org.springframework.data.repository.query.Param("limit") int limit);

    @Modifying
    @Transactional
    @Query("UPDATE BsmOutboxEventEntity e SET e.status = 'PENDING' WHERE e.status = 'IN_FLIGHT'")
    int resetInFlightToPending();
}
