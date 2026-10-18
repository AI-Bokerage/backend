package io.genfin.pricing.internal.promotion;

import io.genfin.api.validation.Validate;
import io.genfin.pricing.port.promotion.PromotionStrategy;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.pricing.PricingRequest;
import io.genfin.pricing.promotion.PromotionRule;
import java.util.List;

/**
 * A {@link PromotionStrategy} backed by a flat list of {@link PromotionRule}s, every one offered as
 * a candidate regardless of its {@link io.genfin.pricing.promotion.PromotionEligibility} - the
 * {@link DefaultPromotionPolicy} filters and picks the winner. Lets an application (or one of the
 * illustrative shapes in {@code io.genfin.pricing.promotion.PromotionStrategies}) declare its
 * promotion catalog as data rather than writing a bespoke {@link PromotionStrategy} per campaign.
 * Mirrors {@code io.genfin.pricing.internal.discount.RuleBasedDiscountStrategy}.
 */
public final class RuleBasedPromotionStrategy implements PromotionStrategy {

  private final List<PromotionRule> rules;

  public RuleBasedPromotionStrategy(List<PromotionRule> rules) {
    this.rules = List.copyOf(rules);
  }

  @Override
  public boolean supports(PricingRequest.Line line, PricingContext context) {
    Validate.notNull(line, "line must not be null.");
    Validate.notNull(context, "context must not be null.");
    return !rules.isEmpty();
  }

  @Override
  public List<PromotionRule> resolve(PricingRequest.Line line, PricingContext context) {
    Validate.notNull(line, "line must not be null.");
    Validate.notNull(context, "context must not be null.");
    return rules;
  }
}
