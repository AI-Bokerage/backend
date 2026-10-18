package io.genfin.pricing.internal.validation.rules;

import io.genfin.pricing.calculation.CalculationIssue;
import io.genfin.pricing.calculation.CalculationResult;
import io.genfin.pricing.rule.RuleResult;
import io.genfin.pricing.validation.ValidationContext;
import io.genfin.pricing.validation.ValidationRule;
import java.util.List;

/**
 * Folds every {@link RuleResult} an already-run {@code
 * io.genfin.pricing.port.rule.CommercialRuleEngine} produced (carried on {@link
 * ValidationContext#ruleResults()}) into this stage's collected {@link CalculationIssue}s, so a
 * commercial-rule violation (minimum price, maximum discount, stacking limit, ...) surfaces
 * alongside every other Pricing Validation finding. Reports nothing when the context carries no
 * rule results - the Commercial Rule Engine is invoked separately and is optional.
 */
public final class RuleValidationRule implements ValidationRule {

  @Override
  public List<CalculationIssue> apply(CalculationResult result, ValidationContext context) {
    return context.ruleResults().stream()
        .map(rule -> CalculationIssue.of(rule.ruleCode(), rule.message(), rule.severity()))
        .toList();
  }
}
