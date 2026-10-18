package io.genfin.reconciliation.internal.validation;

import io.genfin.reconciliation.port.validation.ReconciliationValidator;
import io.genfin.reconciliation.reconciliation.Reconciliation;
import io.genfin.reconciliation.validation.ValidationContext;
import io.genfin.reconciliation.validation.ValidationIssue;
import io.genfin.reconciliation.validation.ValidationResult;
import io.genfin.reconciliation.validation.ValidationRule;
import java.util.ArrayList;
import java.util.List;

/** Runs every rule and collects all issues — never short-circuits on the first failure. */
public final class DefaultReconciliationValidator implements ReconciliationValidator {

  private final List<ValidationRule> rules;

  public DefaultReconciliationValidator(List<ValidationRule> rules) {
    this.rules = List.copyOf(rules);
  }

  @Override
  public ValidationResult validate(Reconciliation reconciliation, ValidationContext context) {
    List<ValidationIssue> issues = new ArrayList<>();
    for (ValidationRule rule : rules) {
      issues.addAll(rule.apply(reconciliation, context));
    }
    return new ValidationResult(issues);
  }
}
