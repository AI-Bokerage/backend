package io.genfin.pricing.internal.rule;

import io.genfin.api.exception.Severity;
import io.genfin.pricing.calculation.CalculationResult;
import io.genfin.pricing.port.rule.CommercialRule;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.rule.RuleContext;
import io.genfin.pricing.rule.RuleResult;
import java.util.List;
import java.util.Optional;

/**
 * Illustrative example rule: the request's {@code "partnerTier"} attribute (see {@link
 * io.genfin.pricing.pricing.PricingAttributes}) must be one of {@code
 * context.allowedPartnerTiers()}. Not applicable unless the caller supplied both the allowed set
 * and the request carries a partner tier attribute.
 */
public final class PartnerPricingRule implements CommercialRule {

  public static final String CODE = "PARTNER_PRICING";
  public static final String PARTNER_TIER_ATTRIBUTE = "partnerTier";

  @Override
  public List<RuleResult> evaluate(
      CalculationResult result, PricingContext context, RuleContext ruleContext) {
    if (ruleContext.allowedPartnerTiers().isEmpty()) {
      return List.of();
    }
    Optional<String> partnerTier = context.attributes().find(PARTNER_TIER_ATTRIBUTE);
    if (partnerTier.isEmpty()) {
      return List.of();
    }
    if (ruleContext.allowedPartnerTiers().get().contains(partnerTier.get())) {
      return List.of();
    }
    return List.of(
        RuleResult.of(
            CODE,
            "Partner tier \"" + partnerTier.get() + "\" is not eligible for this pricing.",
            Severity.ERROR));
  }
}
