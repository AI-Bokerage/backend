package com.company.ppmsvc.planprice.usecase;

import com.company.ppmsvc.common.BillingCycle;
import com.company.ppmsvc.planprice.model.PlanPrice;
import java.util.UUID;

/**
 * Pricing Resolver Engine (PPM-09).
 *
 * <p>Determines the single currently applicable price for a plan given a region,
 * currency, and billing cycle. This is a pure read engine — it never mutates
 * any price, plan, or promo state.
 *
 * <p>Resolution rules applied in order:
 * <ol>
 *   <li>PR-1  Plan must exist (PLAN_NOT_FOUND → 404)</li>
 *   <li>PR-2  Normalise region/currency to uppercase</li>
 *   <li>PR-3  Load candidate rows for (planId, region, currency)</li>
 *   <li>PR-4  Filter: active rows only</li>
 *   <li>PR-5  Filter: effectiveFrom &lt;= today (future rows excluded)</li>
 *   <li>PR-6  Select: row with the latest effectiveFrom</li>
 *   <li>PR-7  No candidate → PLAN_PRICE_NOT_RESOLVED → 404</li>
 * </ol>
 *
 * <p>No currency conversion, no regional fallback, no promo application.
 * Exact matches only.
 */
public interface PricingResolver {

    /**
     * Resolves the currently applicable price.
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code PLAN_NOT_FOUND} when the plan does not exist
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code PLAN_PRICE_NOT_RESOLVED} when no active price matches the criteria
     */
    PlanPrice resolvePrice(UUID planId, String region, String currency, BillingCycle cycle);
}
