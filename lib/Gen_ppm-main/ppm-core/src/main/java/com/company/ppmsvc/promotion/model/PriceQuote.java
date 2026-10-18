package com.company.ppmsvc.promotion.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Result of {@link com.company.ppmsvc.promotion.usecase.PromotionPricingService}'s
 * {@code quote}/{@code quoteWithCustomer}.
 *
 * <p>{@code discountAmount}, {@code promotionId}, and {@code appliedAction}
 * are {@code null} whenever no discount was applied (any reason other than
 * {@link PromotionApplicationReason#VALID}); {@code finalAmount} then equals
 * {@code baseAmount}.
 *
 * <p>{@code conditionsSkipped} is {@code true} when the quote was produced by
 * the legacy {@code quote(...)} overload with no {@link CustomerContext} —
 * eligibility and per-user usage-limit conditions could not be evaluated and
 * were skipped for backward compatibility. It is {@code false} for {@code
 * quoteWithCustomer(...)} calls, where every condition was evaluated.
 *
 * <p>{@code grantedEntitlement} is non-null when the promotion's action is an
 * {@link EntitlementAction} — {@code discountAmount} is then {@code null} and
 * {@code finalAmount} equals {@code baseAmount} (no price change). It is
 * {@code null} for price-action quotes, where {@code discountAmount}/{@code
 * finalAmount} carry the result instead.
 */
public record PriceQuote(
    BigDecimal baseAmount,
    String currency,
    BigDecimal discountAmount,
    BigDecimal finalAmount,
    PromotionApplicationReason reason,
    UUID promotionId,
    PromotionAction appliedAction,
    boolean conditionsSkipped,
    AppliedEntitlement grantedEntitlement
) {
}
