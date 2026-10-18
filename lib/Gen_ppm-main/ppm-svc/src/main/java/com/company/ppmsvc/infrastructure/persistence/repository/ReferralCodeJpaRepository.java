package com.company.ppmsvc.infrastructure.persistence.repository;

import com.company.ppmsvc.infrastructure.persistence.entity.ReferralCodeEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ReferralCodeJpaRepository extends JpaRepository<ReferralCodeEntity, UUID> {

    Optional<ReferralCodeEntity> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByReferralProgramIdAndReferrerCustomerId(UUID referralProgramId, String referrerCustomerId);

    @Transactional
    @Modifying
    @Query("UPDATE ReferralCodeEntity c SET c.deletedAt = :now, c.updatedAt = :now, c.updatedBy = :actorId " +
           "WHERE c.id = :id AND c.deletedAt IS NULL")
    int softDeleteById(@Param("id") UUID id, @Param("now") Instant now, @Param("actorId") UUID actorId);
}
