package com.company.ppmsvc.infrastructure.persistence.repository;

import com.company.ppmsvc.infrastructure.persistence.entity.PromotionEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data JPA repository for {@link PromotionEntity}.
 *
 * <p>Application services must not use this directly — they depend on
 * {@link com.company.ppmsvc.promotion.port.PromotionRepositoryPort}.
 */
public interface PromotionJpaRepository extends JpaRepository<PromotionEntity, UUID> {

    /** Returns all non-deleted promotions ordered by name ascending. */
    List<PromotionEntity> findAllByOrderByNameAsc();

    /** Returns all non-deleted promotions belonging to the given campaign, ordered by name ascending. */
    List<PromotionEntity> findAllByCampaignIdOrderByNameAsc(UUID campaignId);

    /**
     * Soft-deletes a promotion row by setting {@code deleted_at}, {@code updated_at},
     * and {@code updated_by} in a single atomic UPDATE.
     */
    @Transactional
    @Modifying
    @Query("UPDATE PromotionEntity p SET p.deletedAt = :now, p.updatedAt = :now, p.updatedBy = :actorId " +
           "WHERE p.id = :id AND p.deletedAt IS NULL")
    int softDeleteById(@Param("id") UUID id,
                       @Param("now") Instant now,
                       @Param("actorId") UUID actorId);
}
