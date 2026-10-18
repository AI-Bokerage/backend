package com.company.ppmsvc.infrastructure.persistence.repository;

import com.company.ppmsvc.infrastructure.persistence.entity.PlanEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data JPA repository for {@link PlanEntity}.
 *
 * <p>This interface is an infrastructure detail.  Application services must
 * not use it directly — they depend on
 * {@link com.company.ppmsvc.plan.port.PlanRepositoryPort}, whose
 * implementation delegates to this repository via the persistence adapter.
 */
public interface PlanJpaRepository extends JpaRepository<PlanEntity, UUID> {

    /** Returns the plan with the given code, or empty if not found. */
    Optional<PlanEntity> findByCode(String code);

    /** Returns the plan with the given slug, or empty if not found. */
    Optional<PlanEntity> findBySlug(String slug);

    /** Returns {@code true} if a plan with the given code already exists. */
    boolean existsByCode(String code);

    /** Returns {@code true} if a plan with the given slug already exists. */
    boolean existsBySlug(String slug);

    /** Returns all non-deleted plans ordered by code ascending. */
    List<PlanEntity> findAllByOrderByCodeAsc();

    /** Draws the next value from {@code ppm_plan_slug_seq}. Concurrency-safe. */
    @Query(value = "SELECT nextval('ppm_plan_slug_seq')", nativeQuery = true)
    long nextSlugValue();

    /**
     * Soft-deletes a plan by setting {@code deleted_at}, {@code updated_at},
     * and {@code updated_by} in a single atomic UPDATE.
     *
     * <p>Returns the number of rows affected (0 = not found or already deleted).
     */
    @Transactional
    @Modifying
    @Query("UPDATE PlanEntity p SET p.deletedAt = :now, p.updatedAt = :now, p.updatedBy = :actorId " +
           "WHERE p.id = :id AND p.deletedAt IS NULL")
    int softDeleteById(@Param("id") UUID id,
                       @Param("now") Instant now,
                       @Param("actorId") UUID actorId);
}
