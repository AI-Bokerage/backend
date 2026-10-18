package io.genfin.ledger.trialbalance;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.api.event.OccurredAt;
import io.genfin.ledger.id.AccountId;
import io.genfin.ledger.id.AccountingPeriodId;
import io.genfin.ledger.id.JournalEntryId;
import io.genfin.ledger.id.JournalLineId;
import io.genfin.ledger.id.LedgerId;
import io.genfin.ledger.journal.JournalEntry;
import io.genfin.ledger.journal.JournalLine;
import io.genfin.ledger.journal.StandardJournalType;
import io.genfin.ledger.period.AccountingPeriod;
import io.genfin.ledger.period.FiscalYear;
import io.genfin.ledger.period.StandardPeriodGranularity;
import io.genfin.ledger.period.StandardPeriodStatus;
import io.genfin.ledger.port.trialbalance.TrialBalanceCalculator;
import io.genfin.ledger.validation.ValidationResult;
import io.genfin.money.currency.Currency;
import io.genfin.money.currency.CurrencyFactory;
import io.genfin.money.money.Money;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrialBalanceTest {

  private static final Currency USD =
      CurrencyFactory.newCurrency().code("USD").symbol("$").displayName("US Dollar").build();
  private static final AccountId CASH = AccountId.generate();
  private static final AccountId RECEIVABLE = AccountId.generate();
  private static final AccountId REVENUE = AccountId.generate();

  private static final TrialBalanceCalculator CALCULATOR = TrialBalanceCalculators.standard();

  private static AccountingPeriod march() {
    return new AccountingPeriod(
        AccountingPeriodId.generate(),
        StandardPeriodGranularity.MONTHLY,
        new FiscalYear(
            "FY2026",
            new OccurredAt(Instant.parse("2026-01-01T00:00:00Z")),
            new OccurredAt(Instant.parse("2027-01-01T00:00:00Z"))),
        new OccurredAt(Instant.parse("2026-03-01T00:00:00Z")),
        new OccurredAt(Instant.parse("2026-04-01T00:00:00Z")),
        StandardPeriodStatus.OPEN);
  }

  private static JournalEntry postedEntry(
      AccountId debitAccount, AccountId creditAccount, String amount) {
    JournalEntry entry =
        new JournalEntry(
            JournalEntryId.generate(),
            LedgerId.generate(),
            StandardJournalType.STANDARD,
            List.of(
                JournalLine.debit(JournalLineId.generate(), debitAccount, Money.of(amount, USD)),
                JournalLine.credit(
                    JournalLineId.generate(), creditAccount, Money.of(amount, USD))));
    entry.validate();
    entry.post();
    return entry;
  }

  @Test
  void rollsUpBalancesPerAccountAcrossMultiplePostedEntries() {
    List<JournalEntry> entries =
        List.of(postedEntry(CASH, REVENUE, "100.00"), postedEntry(RECEIVABLE, REVENUE, "50.00"));

    TrialBalance trialBalance = CALCULATOR.calculate(march(), entries, USD);

    assertThat(trialBalance.find(CASH)).isPresent();
    assertThat(trialBalance.find(CASH).get().totalDebit()).isEqualTo(Money.of("100.00", USD));
    assertThat(trialBalance.find(REVENUE).get().totalCredit()).isEqualTo(Money.of("150.00", USD));
  }

  @Test
  void debitsEqualCreditsAcrossTheWholeTrialBalance() {
    List<JournalEntry> entries =
        List.of(
            postedEntry(CASH, REVENUE, "100.00"),
            postedEntry(RECEIVABLE, REVENUE, "50.00"),
            postedEntry(CASH, RECEIVABLE, "25.00"));

    TrialBalance trialBalance = CALCULATOR.calculate(march(), entries, USD);

    assertThat(trialBalance.summary().totalDebit()).isEqualTo(trialBalance.summary().totalCredit());
    ValidationResult result = TrialBalanceValidator.validate(trialBalance);
    assertThat(result.isValid()).isTrue();
  }

  @Test
  void excludesEntriesThatHaveNotReachedPosted() {
    JournalEntry unposted =
        new JournalEntry(
            JournalEntryId.generate(),
            LedgerId.generate(),
            StandardJournalType.STANDARD,
            List.of(
                JournalLine.debit(JournalLineId.generate(), CASH, Money.of("10.00", USD)),
                JournalLine.credit(JournalLineId.generate(), REVENUE, Money.of("10.00", USD))));

    TrialBalance trialBalance = CALCULATOR.calculate(march(), List.of(unposted), USD);

    assertThat(trialBalance.entries()).isEmpty();
    assertThat(trialBalance.summary().accountCount()).isZero();
  }

  @Test
  void validatorFlagsAHandBuiltUnbalancedTrialBalance() {
    // A well-formed TrialBalance can never actually be unbalanced (every JournalEntry it is built
    // from is already balanced by construction) - this exercises the validator's arithmetic
    // directly, as defense in depth, mirroring how BalancedPostingRule re-checks a fact already
    // guaranteed elsewhere.
    TrialBalanceSummary unbalanced =
        new TrialBalanceSummary(1, Money.of("100.00", USD), Money.of("90.00", USD));
    TrialBalance trialBalance =
        new TrialBalance(AccountingPeriodId.generate(), List.of(), unbalanced);

    ValidationResult result = TrialBalanceValidator.validate(trialBalance);

    assertThat(result.isValid()).isFalse();
    assertThat(result.issues().get(0).ruleCode()).isEqualTo("TRIAL_BALANCE_UNBALANCED");
  }
}
