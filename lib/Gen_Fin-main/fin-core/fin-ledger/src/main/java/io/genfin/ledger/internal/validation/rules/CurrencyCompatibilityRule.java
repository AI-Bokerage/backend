package io.genfin.ledger.internal.validation.rules;

import io.genfin.api.exception.Severity;
import io.genfin.ledger.journal.JournalEntry;
import io.genfin.ledger.journal.JournalLine;
import io.genfin.ledger.validation.ValidationContext;
import io.genfin.ledger.validation.ValidationIssue;
import io.genfin.ledger.validation.ValidationRule;
import io.genfin.money.currency.Currency;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Every {@link JournalLine} of a {@link JournalEntry} must post in the same {@link Currency}.
 * {@link JournalEntry} already guarantees this by construction (summing mixed currencies throws
 * when the balance invariant is checked) - this rule re-surfaces the same finding as a composable
 * {@link ValidationIssue} instead of an exception, mirroring {@link BalancedPostingRule}.
 */
public final class CurrencyCompatibilityRule implements ValidationRule {

  private static final int SINGLE_CURRENCY = 1;

  @Override
  public List<ValidationIssue> apply(JournalEntry entry, ValidationContext context) {
    Set<Currency> currencies =
        entry.lines().stream()
            .map(CurrencyCompatibilityRule::activeCurrency)
            .collect(Collectors.toSet());
    if (currencies.size() > SINGLE_CURRENCY) {
      return List.of(
          ValidationIssue.of(
              "MULTIPLE_CURRENCIES_IN_ENTRY",
              "a journal entry must use one currency, found " + currencies.size() + ".",
              Severity.CRITICAL));
    }
    return List.of();
  }

  private static Currency activeCurrency(JournalLine line) {
    return line.isDebit() ? line.debit().currency() : line.credit().currency();
  }
}
