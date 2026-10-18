package io.genfin.pricing.coupon;

import io.genfin.money.money.Money;
import io.genfin.pricing.price.PriceAdjustment;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.pricing.PricingRequest;

/**
 * The reduction one redeemed {@link CouponCampaign} contributes once its {@link CouponCode} has
 * been resolved and its {@link CouponEligibility} judged to pass - a code-driven counterpart to
 * {@code io.genfin.pricing.discount.Discount} (which stacks silently) and {@code
 * io.genfin.pricing.promotion.Promotion} (which is auto-triggered): a coupon only ever applies
 * because the customer explicitly presented a {@link CouponCode}. fin-pricing ships no coupon
 * arithmetic of its own - the actual reduction is entirely the consuming application's business
 * rule, built from {@link io.genfin.pricing.price.PriceModifier} and registered via {@link
 * io.genfin.pricing.port.coupon.CouponRegistry}.
 */
@FunctionalInterface
public interface Coupon {

  /**
   * Computes this coupon's {@link PriceAdjustment} against {@code runningAmount} - the line's price
   * after every earlier-applied Discount/Promotion Engine reduction.
   */
  PriceAdjustment applyTo(Money runningAmount, PricingRequest.Line line, PricingContext context);
}
