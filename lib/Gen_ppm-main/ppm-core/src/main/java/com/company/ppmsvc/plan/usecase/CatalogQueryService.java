package com.company.ppmsvc.plan.usecase;

import com.company.ppmsvc.plan.model.CatalogPlanDetailResponse;
import com.company.ppmsvc.plan.model.CatalogPlanSummaryResponse;
import java.util.List;

/**
 * Pure read engine for the public plan catalog (PPM-10).
 *
 * <p>This service assembles composite plan views from multiple domain aggregates
 * (Plan, PlanModule, Module, PlanEntitlement, Entitlement, PlanVersion) and
 * exposes them as lightweight read models.  No writes, no mutations.
 *
 * <p>Intended consumers: REG-SVC (tenant registration), BSM-SVC (subscription
 * management), pricing pages, and checkout flows.
 */
public interface CatalogQueryService {

    /**
     * Returns all active plans with {@code PUBLIC} visibility.
     *
     * <p>Each summary includes the latest active version snapshot. If a plan
     * has no version rows, {@code latestVersion} is {@code null}.
     *
     * <p>Implementation contract: resolves plans and all versions in exactly
     * two queries — no N+1 behaviour.
     */
    List<CatalogPlanSummaryResponse> listPublicPlans();

    /**
     * Returns the full catalog detail for the plan identified by {@code slug}.
     *
     * <p>The detail view includes modules, entitlements (with plan-specific
     * override values), and the latest active version snapshot.
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code PLAN_NOT_FOUND} if no active plan with the given slug exists.
     */
    CatalogPlanDetailResponse getPlanDetail(String slug);
}
