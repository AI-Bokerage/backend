package io.genfin.pricing.discount;

import io.genfin.api.domain.ValueObject;
import io.genfin.money.money.Money;
import io.genfin.pricing.id.DiscountId;
import io.genfin.pricing.port.discount.DiscountPolicy;
import io.genfin.pricing.price.PriceAdjustment;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.pricing.PricingRequest;

/**
 * One reduction the Discount Engine may apply to a line's running {@link Money} amount - a flat
 * percentage ({@link PercentageDiscount}), a fixed amount ({@link FixedDiscount}), a
 * threshold-based rate ({@link TierDiscount}), a quantity-based rate ({@link VolumeDiscount}), or
 * another {@link Discount} conditionally gated on a {@link DiscountCondition} ({@link
 * ConditionalDiscount}). fin-pricing ships these five shapes as pluggable building blocks only -
 * which discounts exist, how they combine, and when they apply is entirely the consuming
 * application's own business rule, resolved via {@link DiscountPolicy}. Mirrors {@link
 * io.genfin.pricing.price.PriceModifier}'s self-describing-adjustment shape, extended with an
 * identity and a human-readable description.
 */
public interface Discount extends ValueObject {

  DiscountId id();

  DiscountType type();

  String description();

  /**
   * Computes this discount's {@link PriceAdjustment} against {@code runningAmount} - the line's
   * price after every earlier-applied discount, not necessarily its original base price, so
   * discounts stack in the order {@link DiscountPolicy} resolved them.
   */
  PriceAdjustment applyTo(Money runningAmount, PricingRequest.Line line, PricingContext context);
}
