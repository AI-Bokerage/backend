package com.company.ppmsvc.infrastructure.persistence.repository;

import com.company.ppmsvc.infrastructure.persistence.entity.CampaignEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface CampaignJpaRepository extends JpaRepository<CampaignEntity, UUID> {

    List<CampaignEntity> findAllByOrderByNameAsc();

    @Transactional
    @Modifying
    @Query("UPDATE CampaignEntity c SET c.deletedAt = :now, c.updatedAt = :now, c.updatedBy = :actorId " +
           "WHERE c.id = :id AND c.deletedAt IS NULL")
    int softDeleteById(@Param("id") UUID id, @Param("now") Instant now, @Param("actorId") UUID actorId);
}
