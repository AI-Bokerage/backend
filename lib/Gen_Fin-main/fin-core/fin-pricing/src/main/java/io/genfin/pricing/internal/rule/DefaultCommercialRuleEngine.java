package io.genfin.pricing.internal.rule;

import io.genfin.api.validation.Validate;
import io.genfin.pricing.calculation.CalculationResult;
import io.genfin.pricing.port.rule.CommercialRule;
import io.genfin.pricing.port.rule.CommercialRuleEngine;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.rule.RuleContext;
import io.genfin.pricing.rule.RuleResult;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs every configured rule and collects all results - never short-circuits on the first rule that
 * reports a problem. Mirrors {@code io.genfin.reconciliation.internal.rule.DefaultRuleEngine}.
 */
public final class DefaultCommercialRuleEngine implements CommercialRuleEngine {

  private final List<CommercialRule> rules;

  public DefaultCommercialRuleEngine(List<CommercialRule> rules) {
    this.rules = List.copyOf(rules);
  }

  @Override
  public List<RuleResult> evaluate(
      CalculationResult result, PricingContext context, RuleContext ruleContext) {
    Validate.notNull(result, "result must not be null.");
    Validate.notNull(context, "context must not be null.");
    Validate.notNull(ruleContext, "ruleContext must not be null.");
    List<RuleResult> results = new ArrayList<>();
    for (CommercialRule rule : rules) {
      results.addAll(rule.evaluate(result, context, ruleContext));
    }
    return List.copyOf(results);
  }
}
