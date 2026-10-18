package io.genfin.ledger.internal.trialbalance;

import io.genfin.api.validation.Validate;
import io.genfin.ledger.journal.JournalEntry;
import io.genfin.ledger.journal.JournalLine;
import io.genfin.ledger.lifecycle.StandardLedgerStatus;
import io.genfin.ledger.period.AccountingPeriod;
import io.genfin.ledger.port.trialbalance.TrialBalanceCalculator;
import io.genfin.ledger.posting.Credit;
import io.genfin.ledger.posting.Debit;
import io.genfin.ledger.posting.PostingBalance;
import io.genfin.ledger.posting.PostingCalculator;
import io.genfin.ledger.posting.PostingEntry;
import io.genfin.ledger.trialbalance.TrialBalance;
import io.genfin.ledger.trialbalance.TrialBalanceEntry;
import io.genfin.ledger.trialbalance.TrialBalanceSummary;
import io.genfin.money.currency.Currency;
import io.genfin.money.money.Money;
import java.util.List;

/**
 * Rolls up every posted {@link JournalLine} of every {@code POSTED} {@link JournalEntry} passed in,
 * per {@link io.genfin.ledger.id.AccountId}, by delegating the arithmetic itself to {@link
 * PostingCalculator} rather than re-implementing debit/credit summation here.
 */
public final class DefaultTrialBalanceCalculator implements TrialBalanceCalculator {

  @Override
  public TrialBalance calculate(
      AccountingPeriod period, List<JournalEntry> entries, Currency currency) {
    Validate.notNull(period, "period must not be null.");
    Validate.notNull(entries, "entries must not be null.");
    Validate.notNull(currency, "currency must not be null.");

    List<PostingEntry> postingEntries =
        entries.stream()
            .filter(DefaultTrialBalanceCalculator::isPosted)
            .flatMap(entry -> entry.lines().stream())
            .map(DefaultTrialBalanceCalculator::toPostingEntry)
            .toList();

    List<PostingBalance> balances = PostingCalculator.balances(postingEntries, currency);
    List<TrialBalanceEntry> trialBalanceEntries =
        balances.stream()
            .map(
                balance ->
                    new TrialBalanceEntry(
                        balance.accountId(), balance.totalDebit(), balance.totalCredit()))
            .toList();

    Money totalDebit = PostingCalculator.totalDebit(postingEntries, currency);
    Money totalCredit = PostingCalculator.totalCredit(postingEntries, currency);
    TrialBalanceSummary summary =
        new TrialBalanceSummary(trialBalanceEntries.size(), totalDebit, totalCredit);

    return new TrialBalance(period.id(), trialBalanceEntries, summary);
  }

  private static boolean isPosted(JournalEntry entry) {
    return entry.status().code().equals(StandardLedgerStatus.POSTED.code());
  }

  private static PostingEntry toPostingEntry(JournalLine line) {
    return line.isDebit()
        ? PostingEntry.of(line.accountId(), Debit.of(line.debit()), line.memo())
        : PostingEntry.of(line.accountId(), Credit.of(line.credit()), line.memo());
  }
}
