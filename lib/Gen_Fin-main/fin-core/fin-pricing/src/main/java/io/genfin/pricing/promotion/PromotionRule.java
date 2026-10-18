package io.genfin.pricing.promotion;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;

/**
 * Pairs a {@link PromotionCampaign} with the {@link Promotion} it contributes and the {@link
 * PromotionEligibility} deciding whether it applies to a given line - an application's declarative
 * promotion catalog entry, consumed by {@link
 * io.genfin.pricing.internal.promotion.RuleBasedPromotionStrategy} (see {@link
 * PromotionStrategies#fromRules}) rather than a bespoke {@code
 * io.genfin.pricing.port.promotion.PromotionStrategy} per campaign. Mirrors {@code
 * io.genfin.pricing.discount.DiscountRule}.
 */
public record PromotionRule(
    PromotionCampaign campaign, Promotion promotion, PromotionEligibility eligibility)
    implements ValueObject {

  public PromotionRule {
    Validate.notNull(campaign, "campaign must not be null.");
    Validate.notNull(promotion, "promotion must not be null.");
    Validate.notNull(eligibility, "eligibility must not be null.");
  }

  public static PromotionRule of(
      PromotionCampaign campaign, Promotion promotion, PromotionEligibility eligibility) {
    return new PromotionRule(campaign, promotion, eligibility);
  }

  /** A rule whose campaign always applies its {@link Promotion}, regardless of context. */
  public static PromotionRule always(PromotionCampaign campaign, Promotion promotion) {
    return new PromotionRule(campaign, promotion, (line, context) -> true);
  }
}
