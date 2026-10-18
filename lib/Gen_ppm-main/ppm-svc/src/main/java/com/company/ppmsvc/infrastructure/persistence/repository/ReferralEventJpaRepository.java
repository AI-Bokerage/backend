package com.company.ppmsvc.infrastructure.persistence.repository;

import com.company.ppmsvc.infrastructure.persistence.entity.ReferralEventEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ReferralEventJpaRepository extends JpaRepository<ReferralEventEntity, UUID> {

    Optional<ReferralEventEntity> findByReferralCodeIdAndReferredCustomerId(UUID referralCodeId, String referredCustomerId);

    int countByReferralCodeIdAndStatus(UUID referralCodeId, String status);

    @Transactional
    @Modifying
    @Query("UPDATE ReferralEventEntity e SET e.deletedAt = :now, e.updatedAt = :now, e.updatedBy = :actorId " +
           "WHERE e.id = :id AND e.deletedAt IS NULL")
    int softDeleteById(@Param("id") UUID id, @Param("now") Instant now, @Param("actorId") UUID actorId);
}
