package com.company.ppmsvc.infrastructure.persistence.repository;

import com.company.ppmsvc.infrastructure.persistence.entity.ReferralProgramEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ReferralProgramJpaRepository extends JpaRepository<ReferralProgramEntity, UUID> {

    List<ReferralProgramEntity> findAllByOrderByNameAsc();

    @Transactional
    @Modifying
    @Query("UPDATE ReferralProgramEntity p SET p.deletedAt = :now, p.updatedAt = :now, p.updatedBy = :actorId " +
           "WHERE p.id = :id AND p.deletedAt IS NULL")
    int softDeleteById(@Param("id") UUID id, @Param("now") Instant now, @Param("actorId") UUID actorId);
}
