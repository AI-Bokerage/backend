package io.genfin.pricing.discount;

import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.pricing.price.Price;
import io.genfin.pricing.price.PriceAdjustment;
import io.genfin.pricing.price.PriceBreakdown;
import io.genfin.pricing.price.PriceComponent;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.pricing.PricingRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure arithmetic over a {@link Price} and the {@link Discount}s resolved for its line: applies
 * each discount in order against the running net amount (so later discounts stack on top of earlier
 * ones) and appends the resulting {@link PriceComponent}s to the {@link PriceBreakdown}. Which
 * discounts apply and how each computes its own reduction is entirely the pluggable {@link
 * Discount}/{@code io.genfin.pricing.port.discount.DiscountStrategy}'s concern - this type only
 * threads the running amount through them, mirroring {@code
 * io.genfin.pricing.calculation.PricingCalculator}.
 */
public final class DiscountCalculator {

  private DiscountCalculator() {}

  public static Price apply(
      Price price, List<Discount> discounts, PricingRequest.Line line, PricingContext context) {
    Validate.notNull(price, "price must not be null.");
    Validate.notNull(discounts, "discounts must not be null.");
    Validate.notNull(line, "line must not be null.");
    Validate.notNull(context, "context must not be null.");
    List<PriceComponent> components = new ArrayList<>(price.breakdown().components());
    Money runningAmount = price.amount();
    for (Discount discount : discounts) {
      PriceAdjustment adjustment = discount.applyTo(runningAmount, line, context);
      components.add(adjustment.toComponent(discount.description()));
      runningAmount = runningAmount.add(adjustment.amount());
    }
    return new Price(price.catalogId(), new PriceBreakdown(components));
  }
}
