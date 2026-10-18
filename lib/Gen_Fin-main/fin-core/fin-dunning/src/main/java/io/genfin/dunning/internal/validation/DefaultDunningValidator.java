package io.genfin.dunning.internal.validation;

import io.genfin.dunning.port.validation.DunningValidator;
import io.genfin.dunning.validation.ValidationContext;
import io.genfin.dunning.validation.ValidationIssue;
import io.genfin.dunning.validation.ValidationResult;
import io.genfin.dunning.validation.ValidationRule;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs every registered {@link ValidationRule} and collects all issues - never short-circuits on
 * the first failure. Mirrors {@code io.genfin.ledger.internal.validation.DefaultJournalValidator} /
 * {@code io.genfin.pricing.internal.validation.DefaultPricingValidator}.
 */
public final class DefaultDunningValidator implements DunningValidator {

  private final List<ValidationRule> rules;

  public DefaultDunningValidator(List<ValidationRule> rules) {
    this.rules = List.copyOf(rules);
  }

  @Override
  public ValidationResult validate(ValidationContext context) {
    List<ValidationIssue> issues = new ArrayList<>();
    for (ValidationRule rule : rules) {
      issues.addAll(rule.apply(context));
    }
    return new ValidationResult(issues);
  }
}
