package io.genfin.pricing.price;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;

/**
 * A single {@link Price}'s totals: what the base price came to, how much every
 * Discount/Promotion/Coupon/Credit/Tax-placeholder/Rounding component added or removed net of that,
 * and the resulting net amount. The per-line counterpart to {@code
 * io.genfin.pricing.pricing.PricingSummary}, which totals across an entire {@code PricingResult}
 * instead of one line.
 */
public record PriceSummary(Money baseAmount, Money adjustmentAmount, Money netAmount)
    implements ValueObject {

  public PriceSummary {
    Validate.notNull(baseAmount, "baseAmount must not be null.");
    Validate.notNull(adjustmentAmount, "adjustmentAmount must not be null.");
    Validate.notNull(netAmount, "netAmount must not be null.");
  }

  public static PriceSummary of(PriceBreakdown breakdown) {
    Validate.notNull(breakdown, "breakdown must not be null.");
    Money base = breakdown.amountOf(PriceType.BASE);
    Money net = breakdown.netAmount();
    return new PriceSummary(base, net.subtract(base), net);
  }
}
