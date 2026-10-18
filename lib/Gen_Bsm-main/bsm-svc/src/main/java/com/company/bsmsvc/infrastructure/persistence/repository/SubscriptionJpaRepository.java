package com.company.bsmsvc.infrastructure.persistence.repository;

import com.company.bsmsvc.domain.enums.SubscriptionStatus;
import com.company.bsmsvc.infrastructure.persistence.entity.SubscriptionEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionJpaRepository extends JpaRepository<SubscriptionEntity, UUID> {

    Optional<SubscriptionEntity> findTopByTenantIdAndStatusInOrderByCreatedAtDesc(UUID tenantId, Collection<SubscriptionStatus> statuses);

    Optional<SubscriptionEntity> findTopByIdAndStatusInOrderByCreatedAtDesc(UUID id, Collection<SubscriptionStatus> statuses);

    @Query("SELECT s FROM SubscriptionEntity s WHERE s.status = com.company.bsmsvc.domain.enums.SubscriptionStatus.ACTIVE AND s.currentPeriodEnd <= :asOf")
    List<SubscriptionEntity> findDueForRenewal(@Param("asOf") Instant asOf);

    Optional<SubscriptionEntity> findByExternalSubscriptionId(String externalSubscriptionId);

    @Query("SELECT s FROM SubscriptionEntity s WHERE s.status = com.company.bsmsvc.domain.enums.SubscriptionStatus.TRIALING AND s.trialEndsAt <= :now")
    List<SubscriptionEntity> findExpiredTrials(@Param("now") Instant now);

    @Query("SELECT s FROM SubscriptionEntity s WHERE s.externalSubscriptionId IS NULL AND s.ppmPlanVersionId IS NULL AND s.status IN :statuses")
    List<SubscriptionEntity> findByExternalSubscriptionIdIsNullAndStatusIn(
        @Param("statuses") Collection<SubscriptionStatus> statuses
    );
}
