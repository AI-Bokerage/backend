package com.company.ppmsvc.promotion.usecase;

import com.company.ppmsvc.common.BillingCycle;
import com.company.ppmsvc.promotion.model.CustomerContext;
import com.company.ppmsvc.promotion.model.PriceQuote;
import java.util.UUID;

/**
 * Composite pricing + promotion engine — the Promotion Processing Pipeline:
 * <pre>
 *   1. Resolve base price        (PricingResolver; 404 on plan/price miss)
 *   2. Resolve coupon            (by code; missing -&gt; COUPON_NOT_FOUND)
 *   3. Resolve promotion         (via coupon.promotionId)
 *   4. Evaluate promotion status (status + validity window)
 *   4.5. Evaluate conditions     (plan restriction, eligibility, usage limits — quoteWithCustomer only)
 *   5. Apply promotion action    (DiscountCalculator)
 *   6. Produce quote             (PriceQuote)
 * </pre>
 * Every later phase extends this pipeline — do not invent a different flow.
 */
public interface PromotionPricingService {

    /**
     * Resolves the price for {@code planId} and applies {@code couponCode}'s
     * promotion, if any. Phase 0 signature, kept backward compatible —
     * without a {@link CustomerContext}, stage 4.5 (conditions) is skipped
     * and {@link PriceQuote#conditionsSkipped()} is {@code true}.
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code PLAN_NOT_FOUND} or {@code PLAN_PRICE_NOT_RESOLVED} if the
     *         plan or price cannot be resolved. All coupon/promotion problems
     *         are returned as a result via {@link PriceQuote#reason()}.
     */
    PriceQuote quote(UUID planId, String region, String currency, BillingCycle cycle, String couponCode);

    /**
     * Same as {@link #quote} but also evaluates the promotion's condition
     * list (stage 4.5) against {@code customer} and the plan, including the
     * per-user usage cap read from the redemption ledger.
     * {@link PriceQuote#conditionsSkipped()} is {@code false}.
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code PLAN_NOT_FOUND} or {@code PLAN_PRICE_NOT_RESOLVED} if the
     *         plan or price cannot be resolved.
     */
    PriceQuote quoteWithCustomer(UUID planId, String region, String currency, BillingCycle cycle,
                                String couponCode, CustomerContext customer);
}
