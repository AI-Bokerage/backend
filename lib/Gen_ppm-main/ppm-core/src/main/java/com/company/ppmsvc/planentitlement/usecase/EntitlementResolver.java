package com.company.ppmsvc.planentitlement.usecase;

import com.company.ppmsvc.planentitlement.model.ResolvedEntitlementResponse;
import java.util.List;
import java.util.UUID;

/**
 * Resolves the effective entitlement set for a subscription plan.
 *
 * <p>This is the single read-path for "what limits and permissions apply to plan X?"
 * It is a pure query operation — no mutations, no side effects.
 *
 * <p>Downstream services (e.g. a future gateway or provisioning engine) inject
 * this interface rather than the full {@link PlanEntitlementApplicationService}.
 */
public interface EntitlementResolver {

    /**
     * Returns the complete set of entitlements assigned to the given plan as
     * resolved {@code code → value} pairs.
     *
     * <p>Implementation contract:
     * <ol>
     *   <li>Verifies the plan exists.</li>
     *   <li>Loads all {@code ppm_plan_entitlements} rows for the plan in one query.</li>
     *   <li>Batch-loads all referenced entitlement definitions in a single query —
     *       no N+1 behaviour.</li>
     *   <li>Returns an empty list if the plan has no entitlement assignments.</li>
     * </ol>
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code PLAN_NOT_FOUND} if the plan does not exist or is soft-deleted.
     */
    List<ResolvedEntitlementResponse> resolveEntitlements(UUID planId);
}
