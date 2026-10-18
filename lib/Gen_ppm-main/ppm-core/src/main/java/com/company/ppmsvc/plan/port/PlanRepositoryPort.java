package com.company.ppmsvc.plan.port;

import com.company.ppmsvc.plan.model.Plan;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Output port (secondary port) for {@link Plan} persistence.
 *
 * <p>Application services depend only on this interface; the concrete
 * implementation is supplied by the host application (see INTEGRATION_GUIDE.md in ppm-core).
 *
 * <p>All methods operate on the {@link Plan} domain model — no JPA types
 * cross this boundary.
 */
public interface PlanRepositoryPort {

    /**
     * Persists a new or updated plan and returns the saved state.
     * The returned instance carries the DB-incremented {@code version}.
     */
    Plan save(Plan plan);

    /** Returns the plan with the given ID, or empty if not found. */
    Optional<Plan> findById(UUID id);

    /** Returns the plan with the given code, or empty if not found. */
    Optional<Plan> findByCode(String code);

    /** Returns the plan with the given slug, or empty if not found. */
    Optional<Plan> findBySlug(String slug);

    /** Returns {@code true} if a plan with the given code already exists. */
    boolean existsByCode(String code);

    /** Returns {@code true} if a plan with the given slug already exists. */
    boolean existsBySlug(String slug);

    /**
     * Draws the next value from the {@code ppm_plan_slug_seq} database sequence.
     *
     * <p>The caller formats the result as {@code "PLN-%04d".formatted(n)} to
     * produce the canonical slug.  Using a DB sequence ensures uniqueness under
     * concurrent writes without relying on {@code SELECT MAX()} or in-memory counters.
     */
    long nextSlugSequenceValue();

    /** Returns all non-deleted plans in the catalog, ordered by code ascending. */
    List<Plan> findAll();

    /**
     * Soft-deletes the plan with the given ID by setting its {@code deletedAt}
     * timestamp.  The plan is invisible to all subsequent queries.
     *
     * <p>Throws {@link com.company.ppmsvc.exception.ResourceNotFoundException}
     * with {@code PLAN_NOT_FOUND} if no active plan with the given ID exists.
     */
    void softDelete(UUID id, UUID actorId);
}
