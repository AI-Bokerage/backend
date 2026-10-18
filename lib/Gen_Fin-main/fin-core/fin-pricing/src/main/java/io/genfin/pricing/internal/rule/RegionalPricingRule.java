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
 * Illustrative example rule: the request's {@code "region"} attribute (see {@link
 * io.genfin.pricing.pricing.PricingAttributes}) must be one of {@code context.allowedRegions()}.
 * Not applicable unless the caller supplied both the allowed set and the request carries a region
 * attribute.
 */
public final class RegionalPricingRule implements CommercialRule {

  public static final String CODE = "REGIONAL_PRICING";
  public static final String REGION_ATTRIBUTE = "region";

  @Override
  public List<RuleResult> evaluate(
      CalculationResult result, PricingContext context, RuleContext ruleContext) {
    if (ruleContext.allowedRegions().isEmpty()) {
      return List.of();
    }
    Optional<String> region = context.attributes().find(REGION_ATTRIBUTE);
    if (region.isEmpty()) {
      return List.of();
    }
    if (ruleContext.allowedRegions().get().contains(region.get())) {
      return List.of();
    }
    return List.of(
        RuleResult.of(
            CODE,
            "Region \"" + region.get() + "\" is not eligible for this pricing.",
            Severity.ERROR));
  }
}
