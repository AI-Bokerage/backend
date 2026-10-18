package com.company.bsmsvc.infrastructure.persistence.repository;

import com.company.bsmsvc.infrastructure.persistence.entity.SubscriptionHistoryEntity;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionHistoryJpaRepository extends JpaRepository<SubscriptionHistoryEntity, UUID> {

	@Query("""
		SELECT h FROM SubscriptionHistoryEntity h
		WHERE (:tenantId IS NULL OR h.tenantId = :tenantId)
		  AND (:subscriptionId IS NULL OR h.subscription.id = :subscriptionId)
		  AND (:action IS NULL OR h.action = :action)
		  AND (:applyDateFrom = false OR h.occurredAt >= :dateFrom)
		  AND (:applyDateTo = false OR h.occurredAt <= :dateTo)
		""")
	Page<SubscriptionHistoryEntity> findHistory(
		@Param("tenantId") UUID tenantId,
		@Param("subscriptionId") UUID subscriptionId,
		@Param("action") String action,
		@Param("applyDateFrom") boolean applyDateFrom,
		@Param("dateFrom") Instant dateFrom,
		@Param("applyDateTo") boolean applyDateTo,
		@Param("dateTo") Instant dateTo,
		Pageable pageable
	);
}
