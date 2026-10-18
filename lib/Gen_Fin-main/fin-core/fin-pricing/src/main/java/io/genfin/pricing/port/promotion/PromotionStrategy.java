package io.genfin.pricing.port.promotion;

import io.genfin.api.port.spi.Extension;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.pricing.PricingRequest;
import io.genfin.pricing.promotion.PromotionRule;
import java.util.List;

/**
 * One application-registered way of resolving which {@link PromotionRule}s may apply to one {@link
 * PricingRequest.Line} - e.g. a seasonal-campaign registry, a bundle-campaign registry. fin-pricing
 * ships no implementation: which campaigns exist and when they are eligible is entirely the
 * consuming application's own business rule, registered here rather than hardcoded (the buy-one-
 * get-one/flash-sale/weekend-sale/early-bird/bundle-discount shapes in {@code
 * io.genfin.pricing.promotion.PromotionStrategies} are illustrative examples built from this same
 * SPI, not special cases of it). Mirrors {@code io.genfin.pricing.port.discount.DiscountStrategy}.
 */
public interface PromotionStrategy extends Extension {

  /** Whether this strategy knows of any candidate {@link PromotionRule}s for {@code line}. */
  boolean supports(PricingRequest.Line line, PricingContext context);

  /**
   * The candidate {@link PromotionRule}s for {@code line}; the caller still filters each by its own
   * {@link io.genfin.pricing.promotion.PromotionEligibility} and picks at most one winner.
   */
  List<PromotionRule> resolve(PricingRequest.Line line, PricingContext context);
}
