package com.company.bsmsvc.infrastructure.persistence.repository;

import com.company.bsmsvc.infrastructure.persistence.entity.SubscriptionEventEntity;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionEventJpaRepository extends JpaRepository<SubscriptionEventEntity, UUID> {

    @Query("""
        SELECT e FROM SubscriptionEventEntity e
        WHERE (:tenantId IS NULL OR e.tenantId = :tenantId)
          AND (:subscriptionId IS NULL OR e.subscription.id = :subscriptionId)
          AND (:eventType IS NULL OR e.eventType = :eventType)
                    AND (:applyDateFrom = false OR e.occurredAt >= :dateFrom)
                    AND (:applyDateTo = false OR e.occurredAt <= :dateTo)
        """)
    Page<SubscriptionEventEntity> findEvents(
        @Param("tenantId") UUID tenantId,
        @Param("subscriptionId") UUID subscriptionId,
        @Param("eventType") String eventType,
                @Param("applyDateFrom") boolean applyDateFrom,
        @Param("dateFrom") Instant dateFrom,
                @Param("applyDateTo") boolean applyDateTo,
        @Param("dateTo") Instant dateTo,
        Pageable pageable
    );
}
