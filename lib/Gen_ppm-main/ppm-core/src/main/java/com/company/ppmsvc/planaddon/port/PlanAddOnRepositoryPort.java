package com.company.ppmsvc.planaddon.port;

import com.company.ppmsvc.planaddon.model.PlanAddOn;
import java.util.List;
import java.util.UUID;

/**
 * Output port (secondary port) for {@link PlanAddOn} persistence.
 *
 * <p>Application services depend only on this interface; the concrete
 * implementation is supplied by the host application (see INTEGRATION_GUIDE.md in ppm-core).
 *
 * <p>All methods operate on the {@link PlanAddOn} domain model — no JPA
 * types cross this boundary.
 */
public interface PlanAddOnRepositoryPort {

    /** Persists a new assignment and returns the saved state. */
    PlanAddOn save(PlanAddOn mapping);

    /**
     * Persists a batch of new assignments in a single database round-trip.
     * The returned list preserves insertion order.
     */
    List<PlanAddOn> saveAll(List<PlanAddOn> mappings);

    /** Returns all assignments for the given plan, in insertion order. */
    List<PlanAddOn> findByPlanId(UUID planId);

    /** Returns all assignments that reference the given add-on. */
    List<PlanAddOn> findByAddOnId(UUID addOnId);

    /** Returns {@code true} if an assignment for (planId, addOnId) already exists. */
    boolean exists(UUID planId, UUID addOnId);

    /**
     * Hard-deletes the assignment for (planId, addOnId).
     * A no-op if no such assignment exists.
     */
    void delete(UUID planId, UUID addOnId);

    /**
     * Hard-deletes all assignments for the given plan.
     * Used by the atomic replace operation.
     */
    void deleteAllByPlanId(UUID planId);
}
