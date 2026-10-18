package com.company.ppmsvc.planentitlement.port;

import com.company.ppmsvc.planentitlement.model.PlanEntitlement;
import java.util.List;
import java.util.UUID;

/**
 * Output port (secondary port) for {@link PlanEntitlement} persistence.
 *
 * <p>Application services depend only on this interface; the concrete
 * implementation is supplied by the host application (see INTEGRATION_GUIDE.md in ppm-core).
 *
 * <p>All methods operate on the {@link PlanEntitlement} domain model — no JPA
 * types cross this boundary.
 */
public interface PlanEntitlementRepositoryPort {

    /** Persists a new assignment and returns the saved state. */
    PlanEntitlement save(PlanEntitlement assignment);

    /**
     * Persists a batch of new assignments in a single database round-trip.
     * The returned list preserves insertion order.
     */
    List<PlanEntitlement> saveAll(List<PlanEntitlement> assignments);

    /** Returns all entitlement assignments for the given plan, in insertion order. */
    List<PlanEntitlement> findByPlanId(UUID planId);

    /** Returns all assignments that reference the given entitlement definition. */
    List<PlanEntitlement> findByEntitlementId(UUID entitlementId);

    /** Returns {@code true} if an assignment for (planId, entitlementId) already exists. */
    boolean exists(UUID planId, UUID entitlementId);

    /**
     * Hard-deletes the assignment for (planId, entitlementId).
     * A no-op if no such assignment exists.
     */
    void delete(UUID planId, UUID entitlementId);

    /**
     * Hard-deletes all assignments for the given plan.
     * Used by the atomic replace operation.
     */
    void deleteAllByPlanId(UUID planId);
}
