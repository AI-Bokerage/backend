package io.genfin.pricing.pricing;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;

/**
 * A point-in-time breakdown of a {@link PricingResult}: what the base price added up to, how much
 * was taken off by the Discount/Promotion/Coupon/Credit engines combined, and the resulting net
 * amount before tax. Detailed per-component figures live in {@code io.genfin.pricing.price} once
 * that package exists; this is the coarse summary.
 */
public record PricingSummary(Money baseAmount, Money reductionAmount, Money netAmount)
    implements ValueObject {

  public PricingSummary {
    Validate.notNull(baseAmount, "baseAmount must not be null.");
    Validate.notNull(reductionAmount, "reductionAmount must not be null.");
    Validate.notNull(netAmount, "netAmount must not be null.");
  }
}
