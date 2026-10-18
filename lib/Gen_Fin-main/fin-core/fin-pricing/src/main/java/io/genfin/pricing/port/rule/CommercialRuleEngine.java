package io.genfin.pricing.port.rule;

import io.genfin.api.port.spi.Extension;
import io.genfin.pricing.calculation.CalculationResult;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.rule.RuleContext;
import io.genfin.pricing.rule.RuleResult;
import java.util.List;

/**
 * Runs every configured {@link CommercialRule} over a priced {@link CalculationResult} and collects
 * all {@link RuleResult}s, never short-circuiting on the first rule that finds a problem. Mirrors
 * {@code io.genfin.reconciliation.port.rule.RuleEngine}.
 */
public interface CommercialRuleEngine extends Extension {

  List<RuleResult> evaluate(
      CalculationResult result, PricingContext context, RuleContext ruleContext);
}
