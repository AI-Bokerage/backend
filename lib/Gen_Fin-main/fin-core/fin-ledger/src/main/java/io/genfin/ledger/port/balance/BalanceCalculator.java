package io.genfin.ledger.port.balance;

import io.genfin.api.event.OccurredAt;
import io.genfin.api.port.spi.Extension;
import io.genfin.ledger.account.Account;
import io.genfin.ledger.balance.Balance;
import io.genfin.ledger.balance.BalanceSnapshot;
import io.genfin.ledger.balance.ClosingBalance;
import io.genfin.ledger.balance.OpeningBalance;
import io.genfin.ledger.balance.RunningBalance;
import io.genfin.ledger.id.AccountId;
import io.genfin.ledger.id.AccountingPeriodId;
import io.genfin.ledger.id.LedgerId;
import io.genfin.ledger.journal.JournalEntry;
import io.genfin.money.currency.Currency;
import java.util.List;

/**
 * The Balance Engine: derives {@link Balance}, {@link RunningBalance}, {@link OpeningBalance} and
 * {@link ClosingBalance} for an {@link Account} from the {@link JournalEntry}s a {@link
 * BalancePolicy} accepts. Never re-derives the double-entry invariant itself - it trusts the {@code
 * JournalEntry}s handed to it are already balanced, exactly as the Posting Engine guarantees them
 * to be by construction.
 */
public interface BalanceCalculator extends Extension {

  /** The total debits/credits {@code accountId} accumulated across {@code entries}. */
  Balance balance(AccountId accountId, List<JournalEntry> entries);

  /**
   * The signed balance of {@code account} immediately after the last (chronologically latest) entry
   * in {@code entries}.
   */
  RunningBalance runningBalance(Account account, List<JournalEntry> entries, OccurredAt asOf);

  /**
   * The balance {@code account} carries into {@code periodId}: derived from {@code priorEntries} if
   * any of them touch the account, or zero in {@code currency} if this is the first period the
   * account participates in.
   */
  OpeningBalance openingBalance(
      Account account,
      AccountingPeriodId periodId,
      List<JournalEntry> priorEntries,
      Currency currency,
      OccurredAt asOf);

  /** {@code opening} plus the net movement {@code periodEntries} add during that period. */
  ClosingBalance closingBalance(
      Account account, OpeningBalance opening, List<JournalEntry> periodEntries, OccurredAt asOf);

  /** A {@link BalanceSnapshot} of every account in {@code accounts}, taken at {@code asOf}. */
  BalanceSnapshot snapshot(
      LedgerId ledgerId, List<Account> accounts, List<JournalEntry> entries, OccurredAt asOf);
}
