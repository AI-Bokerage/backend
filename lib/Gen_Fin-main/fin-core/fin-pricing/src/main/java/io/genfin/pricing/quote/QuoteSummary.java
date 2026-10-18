package io.genfin.pricing.quote;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.pricing.pricing.PricingSummary;

/**
 * A {@link Quote}'s totals: what the base price added up to, how much the Discount/Promotion/
 * Coupon/Credit engines took off combined, and the resulting net amount a customer is being
 * offered. Its own type rather than reusing {@link PricingSummary} directly, mirroring {@code
 * io.genfin.pricing.price.PriceSummary} vs {@code io.genfin.pricing.pricing.PricingSummary}: a
 * Quote's totals are a commercial-offer concern, distinct from a pipeline run's.
 */
public record QuoteSummary(Money baseAmount, Money reductionAmount, Money netAmount)
    implements ValueObject {

  public QuoteSummary {
    Validate.notNull(baseAmount, "baseAmount must not be null.");
    Validate.notNull(reductionAmount, "reductionAmount must not be null.");
    Validate.notNull(netAmount, "netAmount must not be null.");
  }

  public static QuoteSummary of(PricingSummary summary) {
    Validate.notNull(summary, "summary must not be null.");
    return new QuoteSummary(summary.baseAmount(), summary.reductionAmount(), summary.netAmount());
  }
}
