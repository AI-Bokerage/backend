package com.company.ppmsvc.promocodeplan.usecase;

import com.company.ppmsvc.promocodeplan.model.PromoCodePlan;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Application service for managing Promo Code ↔ Plan restrictions.
 *
 * <p>When at least one {@link PromoCodePlan} row exists for a promo code, the
 * code is restricted to only those plans. An unrestricted promo code has no
 * rows and applies to all plans.
 *
 * <p>Framework-agnostic — all methods return or accept domain types and
 * primitives; no DTO or JPA types cross this boundary.
 */
public interface PromoCodePlanApplicationService {

    /**
     * Adds plan restrictions to a promo code.
     *
     * <p>Business rules:
     * <ul>
     *   <li>BR-P1: Every supplied {@code planId} must reference an existing plan.</li>
     *   <li>BR-P2: A duplicate {@code (promoCodeId, planId)} is rejected.</li>
     *   <li>BR-P3: Duplicate IDs in {@code planIds} are already collapsed by {@link Set} semantics.</li>
     * </ul>
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code PROMO_CODE_NOT_FOUND} or {@code PLAN_NOT_FOUND}.
     * @throws com.company.ppmsvc.exception.BusinessException with
     *         {@code PROMO_CODE_PLAN_ALREADY_ASSIGNED} if any plan is already restricted.
     */
    List<PromoCodePlan> assignPlans(UUID actorId, UUID promoCodeId, Set<UUID> planIds);

    /**
     * Atomically replaces the complete set of plan restrictions on a promo code.
     *
     * <p>Business rules:
     * <ul>
     *   <li>BR-P1: Every supplied {@code planId} must reference an existing plan.</li>
     *   <li>BR-P4: All plans are validated first; only after all pass does the delete occur.</li>
     * </ul>
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code PROMO_CODE_NOT_FOUND} or {@code PLAN_NOT_FOUND}.
     */
    List<PromoCodePlan> replacePlans(UUID actorId, UUID promoCodeId, Set<UUID> planIds);

    /**
     * Returns all plan restrictions for the given promo code, in insertion order.
     *
     * <p>BR-P5: Returns all mappings — the caller interprets an empty list as
     * "unrestricted" (applies to all plans).
     */
    List<PromoCodePlan> getRestrictedPlans(UUID promoCodeId);

    /**
     * Removes a single plan restriction from a promo code.
     *
     * <p>Business rules:
     * <ul>
     *   <li>BR-P6: Only the mapping row is deleted — the promo code and plan are untouched.</li>
     *   <li>BR-P7: Throws if no restriction exists for {@code (promoCodeId, planId)}.</li>
     * </ul>
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code PROMO_CODE_PLAN_MAPPING_NOT_FOUND} if no such restriction exists.
     */
    void removePlan(UUID promoCodeId, UUID planId);
}
