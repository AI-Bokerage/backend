package io.genfin.ledger.internal.validation.rules;

import io.genfin.api.exception.Severity;
import io.genfin.ledger.journal.JournalEntry;
import io.genfin.ledger.journal.JournalLine;
import io.genfin.ledger.validation.ValidationContext;
import io.genfin.ledger.validation.ValidationIssue;
import io.genfin.ledger.validation.ValidationRule;
import io.genfin.money.money.Money;
import java.util.ArrayList;
import java.util.List;

/**
 * Every {@link JournalLine}'s active (non-zero) side must carry a strictly positive {@link Money}
 * amount. {@link JournalLine} already enforces this by construction - this rule re-surfaces the
 * same finding as a composable {@link ValidationIssue}, mirroring {@link BalancedPostingRule}.
 */
public final class MoneyValidationRule implements ValidationRule {

  @Override
  public List<ValidationIssue> apply(JournalEntry entry, ValidationContext context) {
    List<ValidationIssue> issues = new ArrayList<>();
    for (JournalLine line : entry.lines()) {
      Money active = line.isDebit() ? line.debit() : line.credit();
      if (!active.isPositive()) {
        issues.add(
            ValidationIssue.of(
                "INVALID_MONEY_AMOUNT",
                "journal line " + line.id().value() + " has a non-positive posted amount.",
                Severity.CRITICAL));
      }
    }
    return issues;
  }
}
