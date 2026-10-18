package com.company.ppmsvc.plan.port;

import com.company.ppmsvc.plan.model.PlanVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain port for {@link PlanVersion} persistence.
 *
 * <p>Application services depend only on this interface; the concrete
 * implementation is supplied by the host application (see INTEGRATION_GUIDE.md in ppm-core).
 *
 * <p>No JPA types cross this interface.
 */
public interface PlanVersionRepositoryPort {

    /**
     * Persists a new or updated {@link PlanVersion} and returns the saved state.
     *
     * <p>Pass {@code version = null} for a new entity so that Spring Data
     * calls {@code persist()} rather than {@code merge()}.
     */
    PlanVersion save(PlanVersion planVersion);

    /** Returns the version with the given ID, or empty if not found (or soft-deleted). */
    Optional<PlanVersion> findById(UUID id);

    /** Returns all non-deleted version rows ordered by {@code planId} asc, {@code versionNo} desc. */
    List<PlanVersion> findAll();

    /** Returns all non-deleted version rows for the given plan, in insertion order. */
    List<PlanVersion> findByPlanId(UUID planId);

    /**
     * Returns all non-deleted version rows for the given plan,
     * ordered by {@code versionNo} descending (most recent first).
     */
    List<PlanVersion> findByPlanIdOrderByVersionNoDesc(UUID planId);

    /**
     * Returns the unique non-deleted version matching {@code (planId, versionNo)},
     * or empty if none exists.
     */
    Optional<PlanVersion> findByPlanIdAndVersionNo(UUID planId, Integer versionNo);

    /**
     * Returns {@code true} if a non-deleted version row exists for
     * {@code (planId, versionNo)}.
     */
    boolean existsByPlanIdAndVersionNo(UUID planId, Integer versionNo);

    /**
     * Soft-deletes the version row with the given ID.
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code PLAN_VERSION_NOT_FOUND} if no active row exists for the given ID.
     */
    void softDelete(UUID id, UUID actorId);
}
