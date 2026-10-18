package io.genfin.ledger.internal.validation;

import io.genfin.ledger.journal.JournalEntry;
import io.genfin.ledger.port.validation.JournalValidator;
import io.genfin.ledger.validation.ValidationContext;
import io.genfin.ledger.validation.ValidationIssue;
import io.genfin.ledger.validation.ValidationResult;
import io.genfin.ledger.validation.ValidationRule;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs every registered {@link ValidationRule} and collects all issues - never short-circuits on
 * the first failure. Mirrors {@code
 * io.genfin.reconciliation.internal.validation.DefaultReconciliationValidator}.
 */
public final class DefaultJournalValidator implements JournalValidator {

  private final List<ValidationRule> rules;

  public DefaultJournalValidator(List<ValidationRule> rules) {
    this.rules = List.copyOf(rules);
  }

  @Override
  public ValidationResult validate(JournalEntry entry, ValidationContext context) {
    List<ValidationIssue> issues = new ArrayList<>();
    for (ValidationRule rule : rules) {
      issues.addAll(rule.apply(entry, context));
    }
    return new ValidationResult(issues);
  }
}
