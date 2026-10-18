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
 * A {@link Discount} that only applies {@code delegate} when {@code condition} matches the line -
 * e.g. "10% off, but only for the APAC region". Applies a zero-amount adjustment when the condition
 * does not match, so the pipeline always sees a component, never a skipped step.
 */
public record ConditionalDiscount(
    DiscountId id, String description, DiscountCondition condition, Discount delegate)
    implements Discount {

  public ConditionalDiscount {
    Validate.notNull(id, "id must not be null.");
    Validate.notBlank(description, "description must not be blank.");
    Validate.notNull(condition, "condition must not be null.");
    Validate.notNull(delegate, "delegate must not be null.");
  }

  public static ConditionalDiscount of(
      String description, DiscountCondition condition, Discount delegate) {
    return new ConditionalDiscount(DiscountId.generate(), description, condition, delegate);
  }

  @Override
  public DiscountType type() {
    return DiscountType.CONDITIONAL;
  }

  @Override
  public PriceAdjustment applyTo(
      Money runningAmount, PricingRequest.Line line, PricingContext context) {
    Validate.notNull(runningAmount, "runningAmount must not be null.");
    Validate.notNull(line, "line must not be null.");
    Validate.notNull(context, "context must not be null.");
    if (condition.test(line, context)) {
      return delegate.applyTo(runningAmount, line, context);
    }
    return PriceAdjustment.of(
        PriceType.DISCOUNT,
        PriceModifier.fixed(Money.zero(runningAmount.currency())),
        runningAmount,
        description + " (condition not met)");
  }
}
