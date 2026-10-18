package com.company.ppmsvc.planentitlement.usecase;

import com.company.ppmsvc.planentitlement.model.ResolvedEntitlementResponse;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Application service for managing Plan ↔ Entitlement assignments.
 *
 * <p>Framework-agnostic — operates exclusively on domain models and
 * primitives. A host application resolves the acting user and translates
 * its own request/response contracts to and from these signatures.
 */
public interface PlanEntitlementApplicationService {

    /**
     * Assigns one or more entitlements to a plan.
     *
     * <p>Business rules:
     * <ul>
     *   <li>BR-P1: Plan must exist.</li>
     *   <li>BR-P2: Each entitlement must exist.</li>
     *   <li>BR-P3: Duplicate {@code (planId, entitlementId)} is rejected.</li>
     *   <li>BR-P4: Duplicate IDs in {@code entitlementIds} are already collapsed by {@link Set} semantics.</li>
     * </ul>
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code PLAN_NOT_FOUND} or {@code ENTITLEMENT_NOT_FOUND}.
     * @throws com.company.ppmsvc.exception.BusinessException with
     *         {@code PLAN_ENTITLEMENT_ALREADY_ASSIGNED} if any entitlement is already present.
     */
    List<ResolvedEntitlementResponse> assignEntitlements(UUID actorId, UUID planId, Set<UUID> entitlementIds);

    /**
     * Atomically replaces the complete set of entitlements on a plan.
     *
     * <p>Business rules:
     * <ul>
     *   <li>BR-P1: Plan must exist.</li>
     *   <li>BR-P2: All entitlements in the request must exist (validated before any delete).</li>
     *   <li>BR-P5: {@code deleteAllByPlanId} + {@code saveAll} execute in one transaction.</li>
     * </ul>
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code PLAN_NOT_FOUND} or {@code ENTITLEMENT_NOT_FOUND}.
     */
    List<ResolvedEntitlementResponse> replaceEntitlements(UUID actorId, UUID planId, Set<UUID> entitlementIds);

    /**
     * Returns all entitlements currently assigned to the plan as resolved code/value pairs.
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code PLAN_NOT_FOUND} if the plan does not exist.
     */
    List<ResolvedEntitlementResponse> getPlanEntitlements(UUID planId);

    /**
     * Removes a single entitlement assignment from a plan.
     *
     * <p>Business rules:
     * <ul>
     *   <li>BR-P1: Plan must exist.</li>
     *   <li>BR-P6: Only the mapping row is deleted — the entitlement catalog entry is untouched.</li>
     *   <li>BR-P7: Throws if the mapping does not exist.</li>
     * </ul>
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code PLAN_NOT_FOUND} or {@code PLAN_ENTITLEMENT_MAPPING_NOT_FOUND}.
     */
    void removeEntitlement(UUID planId, UUID entitlementId);
}
