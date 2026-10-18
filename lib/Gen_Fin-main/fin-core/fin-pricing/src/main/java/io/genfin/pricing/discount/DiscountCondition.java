package io.genfin.pricing.discount;

import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.pricing.PricingRequest;

/**
 * An application-supplied predicate deciding whether a discount applies to one {@link
 * PricingRequest.Line} - e.g. "customer segment is VIP", "region is APAC". fin-pricing ships no
 * conditions of its own: what makes a discount applicable is entirely the consuming application's
 * business rule, supplied here rather than hardcoded, and read via {@link
 * io.genfin.pricing.pricing.PricingAttributes}/{@link io.genfin.pricing.catalog.CatalogAttributes}
 * the application itself populated.
 */
@FunctionalInterface
public interface DiscountCondition {

  boolean test(PricingRequest.Line line, PricingContext context);
}
