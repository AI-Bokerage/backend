package io.genfin.pricing.credit;

import io.genfin.money.money.Money;
import io.genfin.pricing.price.PriceAdjustment;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.pricing.PricingRequest;

/**
 * The reduction one resolved {@link CreditWallet}'s {@link CreditBalance} contributes to a line -
 * fin-pricing ships one standard shape ({@link CreditCalculators#fullBalance()}), but the actual
 * arithmetic (how much of the balance to draw, whether some categories are preferred over others)
 * is a pluggable business rule, built from {@link io.genfin.pricing.price.PriceModifier} and
 * registered via {@link io.genfin.api.spi.ExtensionRegistry}. Mirrors {@code
 * io.genfin.pricing.coupon.Coupon}.
 */
@FunctionalInterface
public interface CreditCalculator {

  /**
   * Computes this calculator's {@link PriceAdjustment} against {@code runningAmount} - the line's
   * price after every earlier-applied Discount/Promotion/Coupon Engine reduction - bounded by what
   * {@code balance} has available.
   */
  PriceAdjustment applyTo(
      Money runningAmount, CreditBalance balance, PricingRequest.Line line, PricingContext context);
}
