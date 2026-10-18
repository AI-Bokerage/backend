package io.genfin.ledger.internal.balance;

import io.genfin.api.event.OccurredAt;
import io.genfin.api.validation.Validate;
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
import io.genfin.ledger.journal.JournalLine;
import io.genfin.ledger.port.balance.BalanceCalculator;
import io.genfin.ledger.port.balance.BalancePolicy;
import io.genfin.money.currency.Currency;
import io.genfin.money.money.Money;
import java.util.ArrayList;
import java.util.List;

/**
 * Default {@link BalanceCalculator}: rolls up the {@link JournalLine}s of every {@link
 * JournalEntry} the configured {@link BalancePolicy} accepts, mirroring how {@code
 * io.genfin.ledger.posting.PostingCalculator} rolls up {@code PostingEntry}s. Pure arithmetic over
 * already-balanced entries - it never re-validates the debit/credit invariant, that is the Posting
 * Engine's job.
 */
public final class DefaultBalanceCalculator implements BalanceCalculator {

  private final BalancePolicy policy;

  public DefaultBalanceCalculator(BalancePolicy policy) {
    this.policy = Validate.notNull(policy, "policy must not be null.");
  }

  @Override
  public Balance balance(AccountId accountId, List<JournalEntry> entries) {
    Validate.notNull(accountId, "accountId must not be null.");
    List<JournalLine> lines = linesFor(accountId, included(entries));
    Validate.argument(!lines.isEmpty(), "no entries touch account " + accountId.value() + ".");
    Money totalDebit = Money.zero(lines.get(0).debit().currency());
    Money totalCredit = Money.zero(lines.get(0).credit().currency());
    for (JournalLine line : lines) {
      totalDebit = totalDebit.add(line.debit());
      totalCredit = totalCredit.add(line.credit());
    }
    return new Balance(accountId, totalDebit, totalCredit);
  }

  @Override
  public RunningBalance runningBalance(
      Account account, List<JournalEntry> entries, OccurredAt asOf) {
    Validate.notNull(account, "account must not be null.");
    Validate.notNull(entries, "entries must not be null.");
    List<JournalEntry> included = included(entries);
    Validate.argument(
        !included.isEmpty(), "no included entries to compute a running balance from.");
    Balance balance = balance(account.id(), included);
    JournalEntry asOfEntry = included.get(included.size() - 1);
    return new RunningBalance(
        account.id(), balance.netAmount(account.classification()), asOfEntry.id(), asOf);
  }

  @Override
  public OpeningBalance openingBalance(
      Account account,
      AccountingPeriodId periodId,
      List<JournalEntry> priorEntries,
      Currency currency,
      OccurredAt asOf) {
    Validate.notNull(account, "account must not be null.");
    Validate.notNull(periodId, "periodId must not be null.");
    Validate.notNull(priorEntries, "priorEntries must not be null.");
    Validate.notNull(currency, "currency must not be null.");
    Validate.notNull(asOf, "asOf must not be null.");
    List<JournalEntry> included = included(priorEntries);
    List<JournalLine> lines = linesFor(account.id(), included);
    Money amount =
        lines.isEmpty()
            ? Money.zero(currency)
            : balance(account.id(), included).netAmount(account.classification());
    return new OpeningBalance(account.id(), periodId, amount, asOf);
  }

  @Override
  public ClosingBalance closingBalance(
      Account account, OpeningBalance opening, List<JournalEntry> periodEntries, OccurredAt asOf) {
    Validate.notNull(account, "account must not be null.");
    Validate.notNull(opening, "opening must not be null.");
    Validate.notNull(periodEntries, "periodEntries must not be null.");
    Validate.notNull(asOf, "asOf must not be null.");
    List<JournalEntry> included = included(periodEntries);
    Money movement =
        included.isEmpty()
            ? Money.zero(opening.amount().currency())
            : balance(account.id(), included).netAmount(account.classification());
    return new ClosingBalance(
        account.id(), opening.periodId(), opening.amount().add(movement), asOf);
  }

  @Override
  public BalanceSnapshot snapshot(
      LedgerId ledgerId, List<Account> accounts, List<JournalEntry> entries, OccurredAt asOf) {
    Validate.notNull(ledgerId, "ledgerId must not be null.");
    Validate.notNull(accounts, "accounts must not be null.");
    List<JournalEntry> included = included(entries);
    List<Balance> balances = new ArrayList<>();
    for (Account account : accounts) {
      List<JournalLine> lines = linesFor(account.id(), included);
      if (!lines.isEmpty()) {
        balances.add(balance(account.id(), included));
      }
    }
    return BalanceSnapshot.of(ledgerId, balances, asOf);
  }

  private List<JournalEntry> included(List<JournalEntry> entries) {
    Validate.notNull(entries, "entries must not be null.");
    return entries.stream().filter(policy::includes).toList();
  }

  private List<JournalLine> linesFor(AccountId accountId, List<JournalEntry> entries) {
    Validate.notNull(entries, "entries must not be null.");
    List<JournalLine> lines = new ArrayList<>();
    for (JournalEntry entry : entries) {
      for (JournalLine line : entry.lines()) {
        if (line.accountId().equals(accountId)) {
          lines.add(line);
        }
      }
    }
    return lines;
  }
}
