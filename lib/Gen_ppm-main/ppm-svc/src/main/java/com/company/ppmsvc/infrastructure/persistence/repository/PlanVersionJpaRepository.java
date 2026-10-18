package com.company.ppmsvc.infrastructure.persistence.repository;

import com.company.ppmsvc.infrastructure.persistence.entity.PlanVersionEntity;
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
 * Spring Data JPA repository for {@link PlanVersionEntity}.
 *
 * <p>This interface is an infrastructure detail.  Application services must
 * not use it directly — they depend on
 * {@link com.company.ppmsvc.plan.port.PlanVersionRepositoryPort}, whose
 * implementation delegates to this repository via the persistence adapter.
 *
 * <p>All derived-query methods respect the {@code @SQLRestriction("deleted_at IS NULL")}
 * declared on {@link PlanVersionEntity}, so only non-deleted rows are returned.
 */
public interface PlanVersionJpaRepository extends JpaRepository<PlanVersionEntity, UUID> {

    /** Returns all non-deleted version rows for the given plan, in insertion order. */
    List<PlanVersionEntity> findByPlanId(UUID planId);

    /** Returns all non-deleted version rows for the given plan, newest version first. */
    List<PlanVersionEntity> findByPlanIdOrderByVersionNoDesc(UUID planId);

    /**
     * Returns the unique non-deleted version matching {@code (planId, versionNo)},
     * or empty if none exists.
     */
    Optional<PlanVersionEntity> findByPlanIdAndVersionNo(UUID planId, Integer versionNo);

    /**
     * Returns {@code true} if a non-deleted version row exists for
     * {@code (planId, versionNo)}.
     */
    boolean existsByPlanIdAndVersionNo(UUID planId, Integer versionNo);

    /**
     * Returns all non-deleted version rows ordered by {@code planId} ascending,
     * {@code versionNo} descending.
     */
    List<PlanVersionEntity> findAllByOrderByPlanIdAscVersionNoDesc();

    /**
     * Soft-deletes a version row by setting {@code deleted_at}, {@code updated_at},
     * and {@code updated_by} in a single atomic UPDATE.
     *
     * <p>Returns the number of rows affected (0 = not found or already deleted).
     */
    @Transactional
    @Modifying
    @Query("UPDATE PlanVersionEntity v SET v.deletedAt = :now, v.updatedAt = :now, v.updatedBy = :actorId " +
           "WHERE v.id = :id AND v.deletedAt IS NULL")
    int softDeleteById(@Param("id") UUID id,
                       @Param("now") Instant now,
                       @Param("actorId") UUID actorId);
}
