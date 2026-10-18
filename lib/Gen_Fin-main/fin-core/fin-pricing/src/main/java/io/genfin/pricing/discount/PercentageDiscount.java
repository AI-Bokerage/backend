package io.genfin.pricing.discount;

import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.money.percentage.Percentage;
import io.genfin.pricing.id.DiscountId;
import io.genfin.pricing.price.PriceAdjustment;
import io.genfin.pricing.price.PriceModifier;
import io.genfin.pricing.price.PriceType;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.pricing.PricingRequest;

/**
 * A {@link Discount} that reduces the running amount by a flat {@link Percentage}, e.g. "10% off".
 */
public record PercentageDiscount(DiscountId id, String description, Percentage percentage)
    implements Discount {

  public PercentageDiscount {
    Validate.notNull(id, "id must not be null.");
    Validate.notBlank(description, "description must not be blank.");
    Validate.notNull(percentage, "percentage must not be null.");
  }

  public static PercentageDiscount of(String description, Percentage percentage) {
    return new PercentageDiscount(DiscountId.generate(), description, percentage);
  }

  @Override
  public DiscountType type() {
    return DiscountType.PERCENTAGE;
  }

  @Override
  public PriceAdjustment applyTo(
      Money runningAmount, PricingRequest.Line line, PricingContext context) {
    Validate.notNull(runningAmount, "runningAmount must not be null.");
    Validate.notNull(line, "line must not be null.");
    Validate.notNull(context, "context must not be null.");
    return PriceAdjustment.of(
        PriceType.DISCOUNT,
        PriceModifier.percentage(percentage.negate()),
        runningAmount,
        description);
  }
}
