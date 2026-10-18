package io.genfin.pricing.price;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.pricing.id.CatalogId;

/**
 * The priced amount the Pricing Pipeline resolved for one {@link
 * io.genfin.pricing.pricing.PricingRequest.Line} - its {@link PriceBreakdown} of base price,
 * discounts, promotions, coupons, credits and tax-placeholder components, collapsed to a net {@link
 * Money} figure. Immutable; a repriced line becomes a new {@code Price}, never a mutation of this
 * one.
 */
public record Price(CatalogId catalogId, PriceBreakdown breakdown) implements ValueObject {

  public Price {
    Validate.notNull(catalogId, "catalogId must not be null.");
    Validate.notNull(breakdown, "breakdown must not be null.");
  }

  public Money amount() {
    return breakdown.netAmount();
  }
}
