package io.genfin.pricing.coupon;

import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.pricing.PricingRequest;

/**
 * An application-supplied predicate deciding whether a redeemed {@link CouponCampaign} applies to
 * one {@link PricingRequest.Line} - e.g. "order total is at least $50", "customer segment is
 * first-time buyer". fin-pricing ships no eligibility rules of its own: what makes a presented code
 * eligible beyond simply existing is entirely the consuming application's business rule. Mirrors
 * {@code io.genfin.pricing.promotion.PromotionEligibility}.
 */
@FunctionalInterface
public interface CouponEligibility {

  boolean test(PricingRequest.Line line, PricingContext context);
}
