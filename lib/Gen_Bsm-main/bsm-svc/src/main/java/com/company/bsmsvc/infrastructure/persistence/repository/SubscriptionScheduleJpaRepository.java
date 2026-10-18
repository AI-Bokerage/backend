package com.company.bsmsvc.infrastructure.persistence.repository;

import com.company.bsmsvc.infrastructure.persistence.entity.SubscriptionScheduleEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionScheduleJpaRepository extends JpaRepository<SubscriptionScheduleEntity, UUID> {

    @Query("""
        SELECT s FROM SubscriptionScheduleEntity s
        WHERE s.subscription.id = :subscriptionId
          AND s.actionType = :actionType
          AND s.status = 'PENDING'
        ORDER BY s.createdAt DESC
        """)
    Optional<SubscriptionScheduleEntity> findPendingBySubscriptionIdAndActionType(
        @Param("subscriptionId") UUID subscriptionId,
        @Param("actionType") String actionType
    );

    @Query("""
        SELECT s FROM SubscriptionScheduleEntity s
        WHERE s.status = 'PENDING'
          AND s.effectiveAt <= :asOf
        ORDER BY s.effectiveAt ASC
        """)
    java.util.List<SubscriptionScheduleEntity> findDueSchedules(@Param("asOf") java.time.Instant asOf);

    @Query("""
        SELECT s FROM SubscriptionScheduleEntity s
        WHERE (:tenantId IS NULL OR s.tenantId = :tenantId)
          AND (:subscriptionId IS NULL OR s.subscription.id = :subscriptionId)
          AND (:status IS NULL OR s.status = :status)
          AND (:actionType IS NULL OR s.actionType = :actionType)
        """)
    Page<SubscriptionScheduleEntity> findSchedules(
        @Param("tenantId") UUID tenantId,
        @Param("subscriptionId") UUID subscriptionId,
        @Param("status") String status,
        @Param("actionType") String actionType,
        Pageable pageable
    );
}
