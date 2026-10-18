package io.genfin.ledger.internal.validation.rules;

import io.genfin.api.exception.Severity;
import io.genfin.ledger.journal.JournalEntry;
import io.genfin.ledger.journal.JournalLine;
import io.genfin.ledger.posting.Credit;
import io.genfin.ledger.posting.Debit;
import io.genfin.ledger.posting.PostingCalculator;
import io.genfin.ledger.posting.PostingEntry;
import io.genfin.ledger.validation.ValidationContext;
import io.genfin.ledger.validation.ValidationIssue;
import io.genfin.ledger.validation.ValidationRule;
import io.genfin.money.currency.Currency;
import io.genfin.money.exception.CurrencyMismatchException;
import java.util.List;

/**
 * Re-surfaces the double-entry invariant {@link JournalEntry} already enforces by construction
 * (Stage 3a) as a composable {@link ValidationIssue} rather than an exception, so callers running a
 * full validation pass see it alongside every other finding. A well-formed {@link JournalEntry} can
 * never actually fail this check - it exists purely as defense in depth, mirroring {@code
 * io.genfin.reconciliation.internal.validation.rules.UnresolvedDiscrepancyRule}.
 */
public final class BalancedPostingRule implements ValidationRule {

  @Override
  public List<ValidationIssue> apply(JournalEntry entry, ValidationContext context) {
    List<JournalLine> lines = entry.lines();
    Currency currency = firstCurrency(lines);
    List<PostingEntry> postingEntries =
        lines.stream().map(BalancedPostingRule::toPostingEntry).toList();
    try {
      if (!PostingCalculator.isBalanced(postingEntries, currency)) {
        return List.of(
            ValidationIssue.of(
                "UNBALANCED_JOURNAL_ENTRY",
                "total debits must equal total credits.",
                Severity.CRITICAL));
      }
    } catch (CurrencyMismatchException e) {
      return List.of(
          ValidationIssue.of(
              "MIXED_CURRENCY_JOURNAL_ENTRY",
              "a journal entry must use one currency: " + e.getMessage(),
              Severity.CRITICAL));
    }
    return List.of();
  }

  private static Currency firstCurrency(List<JournalLine> lines) {
    JournalLine first = lines.get(0);
    return first.isDebit() ? first.debit().currency() : first.credit().currency();
  }

  private static PostingEntry toPostingEntry(JournalLine line) {
    return line.isDebit()
        ? PostingEntry.of(line.accountId(), Debit.of(line.debit()), line.memo())
        : PostingEntry.of(line.accountId(), Credit.of(line.credit()), line.memo());
  }
}
