package io.genfin.ledger.internal.validation.rules;

import io.genfin.api.exception.Severity;
import io.genfin.ledger.journal.JournalEntry;
import io.genfin.ledger.period.AccountingPeriod;
import io.genfin.ledger.validation.ValidationContext;
import io.genfin.ledger.validation.ValidationIssue;
import io.genfin.ledger.validation.ValidationRule;
import java.util.List;

/**
 * A {@link JournalEntry} may only post into an {@link AccountingPeriod} that is still {@link
 * AccountingPeriod#isOpen()}. Reports no issue when the context carries no period - the rule has
 * nothing to check it against.
 */
public final class PeriodOpenRule implements ValidationRule {

  @Override
  public List<ValidationIssue> apply(JournalEntry entry, ValidationContext context) {
    AccountingPeriod period = context.period();
    if (period == null) {
      return List.of();
    }
    if (!period.isOpen()) {
      return List.of(
          ValidationIssue.of(
              "PERIOD_NOT_OPEN",
              "accounting period " + period.id().value() + " is " + period.status().code() + ".",
              Severity.ERROR));
    }
    return List.of();
  }
}
