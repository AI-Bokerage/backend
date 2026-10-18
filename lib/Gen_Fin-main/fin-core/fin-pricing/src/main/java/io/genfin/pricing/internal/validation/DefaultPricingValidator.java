package io.genfin.pricing.internal.validation;

import io.genfin.pricing.calculation.CalculationIssue;
import io.genfin.pricing.calculation.CalculationResult;
import io.genfin.pricing.port.validation.PricingValidator;
import io.genfin.pricing.validation.ValidationContext;
import io.genfin.pricing.validation.ValidationResult;
import io.genfin.pricing.validation.ValidationRule;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs every registered {@link ValidationRule} and collects all issues - never short-circuits on
 * the first failure. Mirrors {@code io.genfin.ledger.internal.validation.DefaultJournalValidator}.
 */
public final class DefaultPricingValidator implements PricingValidator {

  private final List<ValidationRule> rules;

  public DefaultPricingValidator(List<ValidationRule> rules) {
    this.rules = List.copyOf(rules);
  }

  @Override
  public ValidationResult validate(CalculationResult result, ValidationContext context) {
    List<CalculationIssue> issues = new ArrayList<>();
    for (ValidationRule rule : rules) {
      issues.addAll(rule.apply(result, context));
    }
    return new ValidationResult(issues);
  }
}
