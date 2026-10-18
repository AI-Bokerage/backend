package com.company.ppmsvc.planprice.usecase;

import com.company.ppmsvc.common.BillingCycle;
import com.company.ppmsvc.planprice.model.PlanPrice;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Application service for the Pricing Catalog.
 *
 * <p>Manages the lifecycle of plan price entries (create, update, delete, query).
 * All methods operate on domain types — no DTOs cross this boundary.
 *
 * <p>Pricing identity is {@code (planId, region, currency, cycle, effectiveFrom)}.
 * A plan may have multiple prices across regions, currencies, billing cycles, and
 * effective dates.
 */
public interface PlanPriceApplicationService {

    /**
     * Creates a new plan price entry.
     *
     * <p>Business rules:
     * <ul>
     *   <li>BR-1: The referenced plan must exist.</li>
     *   <li>BR-2: {@code amount} must be positive (greater than zero).</li>
     *   <li>BR-3: {@code currency} is normalised to uppercase before persistence.</li>
     *   <li>BR-4: {@code region} is normalised to uppercase before persistence.</li>
     *   <li>BR-5: {@code taxInclusive} defaults to {@code false} when not supplied.</li>
     *   <li>BR-7: Duplicate active pricing key is rejected before the write.</li>
     * </ul>
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code PLAN_NOT_FOUND} if the plan does not exist.
     * @throws com.company.ppmsvc.exception.BusinessException with
     *         {@code VALIDATION_ERROR} if {@code amount} is zero or negative.
     * @throws com.company.ppmsvc.exception.BusinessException with
     *         {@code PLAN_PRICE_ALREADY_EXISTS} if the pricing key is already active.
     */
    PlanPrice createPrice(UUID actorId, UUID planId, BillingCycle cycle, String currency,
        String region, BigDecimal amount, Boolean taxInclusive, LocalDate effectiveFrom);

    /**
     * Partially updates an existing plan price entry (PATCH semantics).
     *
     * <p>Only {@code amount}, {@code taxInclusive}, and {@code active} may be changed.
     * The pricing identity fields are immutable after creation. A {@code null}
     * argument means "leave unchanged".
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code PLAN_PRICE_NOT_FOUND} if the price does not exist or is soft-deleted.
     * @throws com.company.ppmsvc.exception.BusinessException with
     *         {@code VALIDATION_ERROR} if a supplied {@code amount} is zero or negative.
     */
    PlanPrice updatePrice(UUID actorId, UUID priceId, BigDecimal amount, Boolean taxInclusive, Boolean active);

    /**
     * Returns a single plan price by its ID.
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code PLAN_PRICE_NOT_FOUND} if the price does not exist or is soft-deleted.
     */
    PlanPrice getPrice(UUID priceId);

    /**
     * Returns all non-deleted plan prices for a plan (or all plans when {@code planId}
     * is null), optionally filtered by region, currency, cycle, and active flag.
     * A {@code null} field means "no filter on that dimension".
     */
    List<PlanPrice> listPrices(UUID planId, String region, String currency, BillingCycle cycle, Boolean active);

    /**
     * Soft-deletes an existing plan price entry.
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code PLAN_PRICE_NOT_FOUND} if the price does not exist or is already deleted.
     */
    void deletePrice(UUID actorId, UUID priceId);
}
