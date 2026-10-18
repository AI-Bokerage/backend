package com.company.ppmsvc.referral.usecase;

import com.company.ppmsvc.common.BillingCycle;
import com.company.ppmsvc.promotion.model.CustomerContext;
import com.company.ppmsvc.promotion.model.PriceQuote;
import java.util.UUID;

/**
 * Referral quote pipeline — mirrors the Phase 0/1 coupon pipeline but swaps
 * stages 2–3 to resolve a referral code's reward promotion instead of a
 * coupon's:
 * <pre>
 *   1. Resolve base price           (PricingResolver — reused)
 *   2. Resolve referral code        (by code; missing -&gt; REFERRAL_CODE_NOT_FOUND)
 *   3. Resolve reward promotion     (via code -&gt; program -&gt; referredRewardPromotionId)
 *   4. Evaluate promotion status    (status + validity window)
 *   4.5. Evaluate conditions        (ConditionEvaluator — reused)
 *   5. Apply action                 (DiscountCalculator — reused)
 *   6. Produce quote                (PriceQuote — same record)
 * </pre>
 * Referral always requires a customer — there is no legacy no-customer path,
 * so {@code conditionsSkipped} is always {@code false}.
 */
public interface ReferralPricingService {

    /**
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code PLAN_NOT_FOUND} or {@code PLAN_PRICE_NOT_RESOLVED} if the plan or
     *         price cannot be resolved. All referral-code/promotion problems are
     *         returned as a result via {@link PriceQuote#reason()}.
     */
    PriceQuote quote(UUID planId, String region, String currency, BillingCycle cycle, String referralCode,
                     CustomerContext customer);
}
