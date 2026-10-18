package io.genfin.ledger.balance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.event.OccurredAt;
import io.genfin.ledger.account.Account;
import io.genfin.ledger.account.AccountAttributes;
import io.genfin.ledger.account.AccountClassification;
import io.genfin.ledger.account.AccountMetadata;
import io.genfin.ledger.account.AccountType;
import io.genfin.ledger.id.AccountId;
import io.genfin.ledger.id.AccountingPeriodId;
import io.genfin.ledger.id.ChartOfAccountsId;
import io.genfin.ledger.id.JournalLineId;
import io.genfin.ledger.id.LedgerId;
import io.genfin.ledger.journal.JournalBuilder;
import io.genfin.ledger.journal.JournalEntry;
import io.genfin.ledger.journal.JournalLine;
import io.genfin.ledger.port.balance.BalanceCalculator;
import io.genfin.money.currency.Currency;
import io.genfin.money.currency.CurrencyFactory;
import io.genfin.money.money.Money;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class BalanceCalculatorTest {

  private static final Currency USD =
      CurrencyFactory.newCurrency().code("USD").symbol("$").displayName("US Dollar").build();

  private static final OccurredAt NOW = new OccurredAt(Instant.parse("2026-07-31T00:00:00Z"));

  private static final AccountType CASH_TYPE =
      new AccountType() {
        @Override
        public String code() {
          return "CASH";
        }

        @Override
        public AccountClassification classification() {
          return AccountClassification.ASSET;
        }
      };

  private static Account cashAccount() {
    ChartOfAccountsId chartId = ChartOfAccountsId.generate();
    return new Account(
        AccountId.generate(),
        chartId,
        "1000",
        "Cash",
        CASH_TYPE,
        null,
        null,
        AccountAttributes.standard(),
        AccountMetadata.empty());
  }

  private static JournalEntry postedEntry(Account cash, AccountId contra, Money amount) {
    JournalEntry entry =
        JournalBuilder.newEntry()
            .ledgerId(LedgerId.generate())
            .line(JournalLine.debit(JournalLineId.generate(), cash.id(), amount))
            .line(JournalLine.credit(JournalLineId.generate(), contra, amount))
            .build();
    entry.validate();
    entry.post();
    return entry;
  }

  @Test
  void rollsUpDebitsAndCreditsForOneAccount() {
    Account cash = cashAccount();
    AccountId revenue = AccountId.generate();
    List<JournalEntry> entries =
        List.of(
            postedEntry(cash, revenue, Money.of("10.00", USD)),
            postedEntry(cash, revenue, Money.of("5.00", USD)));

    BalanceCalculator calculator = BalanceCalculators.of(BalanceCalculators.standardPolicy());
    Balance balance = calculator.balance(cash.id(), entries);

    assertThat(balance.totalDebit()).isEqualTo(Money.of("15.00", USD));
    assertThat(balance.totalCredit()).isEqualTo(Money.zero(USD));
    assertThat(balance.netAmount(AccountClassification.ASSET)).isEqualTo(Money.of("15.00", USD));
  }

  @Test
  void excludesEntriesNotYetPostedFromTheBalance() {
    Account cash = cashAccount();
    AccountId revenue = AccountId.generate();
    JournalEntry unposted =
        JournalBuilder.newEntry()
            .ledgerId(LedgerId.generate())
            .line(JournalLine.debit(JournalLineId.generate(), cash.id(), Money.of("10.00", USD)))
            .line(JournalLine.credit(JournalLineId.generate(), revenue, Money.of("10.00", USD)))
            .build();
    List<JournalEntry> entries =
        List.of(unposted, postedEntry(cash, revenue, Money.of("5.00", USD)));

    BalanceCalculator calculator = BalanceCalculators.of(BalanceCalculators.standardPolicy());
    Balance balance = calculator.balance(cash.id(), entries);

    assertThat(balance.totalDebit()).isEqualTo(Money.of("5.00", USD));
  }

  @Test
  void computesARunningBalanceAsOfTheLastIncludedEntry() {
    Account cash = cashAccount();
    AccountId revenue = AccountId.generate();
    List<JournalEntry> entries =
        List.of(
            postedEntry(cash, revenue, Money.of("10.00", USD)),
            postedEntry(cash, revenue, Money.of("5.00", USD)));

    BalanceCalculator calculator = BalanceCalculators.of(BalanceCalculators.standardPolicy());
    RunningBalance running = calculator.runningBalance(cash, entries, NOW);

    assertThat(running.amount()).isEqualTo(Money.of("15.00", USD));
    assertThat(running.asOfEntryId()).isEqualTo(entries.get(1).id());
  }

  @Test
  void openingBalanceIsZeroWhenAnAccountHasNoPriorEntries() {
    Account cash = cashAccount();
    BalanceCalculator calculator = BalanceCalculators.of(BalanceCalculators.standardPolicy());

    OpeningBalance opening =
        calculator.openingBalance(cash, AccountingPeriodId.generate(), List.of(), USD, NOW);

    assertThat(opening.amount()).isEqualTo(Money.zero(USD));
  }

  @Test
  void closingBalanceAddsThePeriodsNetMovementToTheOpeningBalance() {
    Account cash = cashAccount();
    AccountId revenue = AccountId.generate();
    OpeningBalance opening =
        new OpeningBalance(cash.id(), AccountingPeriodId.generate(), Money.of("100.00", USD), NOW);
    List<JournalEntry> periodEntries = List.of(postedEntry(cash, revenue, Money.of("25.00", USD)));

    BalanceCalculator calculator = BalanceCalculators.of(BalanceCalculators.standardPolicy());
    ClosingBalance closing = calculator.closingBalance(cash, opening, periodEntries, NOW);

    assertThat(closing.amount()).isEqualTo(Money.of("125.00", USD));
    assertThat(closing.periodId()).isEqualTo(opening.periodId());
  }

  @Test
  void snapshotOnlyIncludesAccountsThatWereActuallyTouched() {
    Account cash = cashAccount();
    Account untouched = cashAccount();
    AccountId revenue = AccountId.generate();
    List<JournalEntry> entries = List.of(postedEntry(cash, revenue, Money.of("10.00", USD)));

    BalanceCalculator calculator = BalanceCalculators.of(BalanceCalculators.standardPolicy());
    BalanceSnapshot snapshot =
        calculator.snapshot(LedgerId.generate(), List.of(cash, untouched), entries, NOW);

    assertThat(snapshot.balances()).extracting(Balance::accountId).containsExactly(cash.id());
  }

  @Test
  void balanceHistoryIsAppendOnly() {
    BalanceSnapshot first = BalanceSnapshot.of(LedgerId.generate(), List.of(), NOW);
    BalanceSnapshot second = BalanceSnapshot.of(LedgerId.generate(), List.of(), NOW);

    BalanceHistory history = BalanceHistory.initial(first).append(second);

    assertThat(history.snapshots()).containsExactly(first, second);
    assertThat(history.latest()).isEqualTo(second);
  }

  @Test
  void balanceSummaryReportsWhetherTheLedgerIsInBalance() {
    Balance balanced =
        new Balance(AccountId.generate(), Money.of("10.00", USD), Money.of("10.00", USD));

    BalanceSummary summary = BalanceSummary.of(List.of(balanced), USD);

    assertThat(summary.isBalanced()).isTrue();
    assertThat(summary.totalDebit()).isEqualTo(Money.of("10.00", USD));
  }

  @Test
  void rejectsComputingABalanceForAnAccountNoEntryTouches() {
    BalanceCalculator calculator = BalanceCalculators.of(BalanceCalculators.standardPolicy());

    assertThatThrownBy(() -> calculator.balance(AccountId.generate(), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
