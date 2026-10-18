package io.genfin.pricing.promotion;

import io.genfin.money.money.Money;
import io.genfin.pricing.price.PriceAdjustment;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.pricing.PricingRequest;

/**
 * The reduction one {@link PromotionCampaign} contributes once it has been judged eligible for a
 * line - a marketing-driven counterpart to {@code io.genfin.pricing.discount.Discount}, always
 * gated by a {@link PromotionEligibility} rather than applied unconditionally. fin-pricing ships no
 * campaign shapes of its own (no BOGO, no flash sale): which promotions exist, their eligibility
 * and their arithmetic are entirely the consuming application's business rule, built from {@link
 * io.genfin.pricing.price.PriceModifier} and registered via {@link
 * io.genfin.pricing.port.promotion.PromotionStrategy}. See {@link PromotionStrategies} for
 * illustrative example shapes.
 */
@FunctionalInterface
public interface Promotion {

  /**
   * Computes this promotion's {@link PriceAdjustment} against {@code runningAmount} - the line's
   * price after every earlier-applied Discount Engine reduction.
   */
  PriceAdjustment applyTo(Money runningAmount, PricingRequest.Line line, PricingContext context);
}
