package io.genfin.pricing.discount;

import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.pricing.id.DiscountId;
import io.genfin.pricing.price.PriceAdjustment;
import io.genfin.pricing.price.PriceModifier;
import io.genfin.pricing.price.PriceType;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.pricing.PricingRequest;

/**
 * A {@link Discount} that reduces the running amount by a flat {@link Money} amount, regardless of
 * the amount's size, e.g. "$10 off".
 */
public record FixedDiscount(DiscountId id, String description, Money amount) implements Discount {

  public FixedDiscount {
    Validate.notNull(id, "id must not be null.");
    Validate.notBlank(description, "description must not be blank.");
    Validate.notNull(amount, "amount must not be null.");
  }

  public static FixedDiscount of(String description, Money amount) {
    return new FixedDiscount(DiscountId.generate(), description, amount);
  }

  @Override
  public DiscountType type() {
    return DiscountType.FIXED;
  }

  @Override
  public PriceAdjustment applyTo(
      Money runningAmount, PricingRequest.Line line, PricingContext context) {
    Validate.notNull(runningAmount, "runningAmount must not be null.");
    Validate.notNull(line, "line must not be null.");
    Validate.notNull(context, "context must not be null.");
    return PriceAdjustment.of(
        PriceType.DISCOUNT, PriceModifier.fixed(amount.negate()), runningAmount, description);
  }
}
