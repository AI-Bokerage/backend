package io.genfin.pricing.promotion;

import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.pricing.PricingRequest;

/**
 * An application-supplied predicate deciding whether a {@link PromotionCampaign} applies to one
 * {@link PricingRequest.Line} - e.g. "quantity is at least 2" (buy-one-get-one), "now falls on a
 * weekend" (weekend sale), "another catalog item is also in the request" (bundle discount).
 * fin-pricing ships no eligibility rules of its own: what makes a campaign eligible is entirely the
 * consuming application's business rule. Mirrors {@code
 * io.genfin.pricing.discount.DiscountCondition}.
 */
@FunctionalInterface
public interface PromotionEligibility {

  boolean test(PricingRequest.Line line, PricingContext context);
}
