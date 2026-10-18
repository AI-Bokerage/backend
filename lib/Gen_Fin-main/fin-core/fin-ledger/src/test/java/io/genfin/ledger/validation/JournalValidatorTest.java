package io.genfin.ledger.validation;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.api.event.OccurredAt;
import io.genfin.ledger.account.Account;
import io.genfin.ledger.account.AccountClassification;
import io.genfin.ledger.account.AccountType;
import io.genfin.ledger.account.ChartOfAccounts;
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
import io.genfin.ledger.port.validation.JournalValidator;
import io.genfin.money.currency.Currency;
import io.genfin.money.currency.CurrencyFactory;
import io.genfin.money.money.Money;
import io.genfin.refund.reference.Reference;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class JournalValidatorTest {

  private static final Currency USD =
      CurrencyFactory.newCurrency().code("USD").symbol("$").displayName("US Dollar").build();
  private static final JournalValidator VALIDATOR = Validators.standard();
  private static final Instant NOW = Instant.parse("2026-03-15T00:00:00Z");

  private enum TestAccountType implements AccountType {
    ASSET_TYPE(AccountClassification.ASSET);

    private final AccountClassification classification;

    TestAccountType(AccountClassification classification) {
      this.classification = classification;
    }

    @Override
    public String code() {
      return name();
    }

    @Override
    public AccountClassification classification() {
      return classification;
    }
  }

  private static JournalEntry entry(AccountId debitAccount, AccountId creditAccount) {
    JournalEntry entry =
        new JournalEntry(
            JournalEntryId.generate(),
            LedgerId.generate(),
            StandardJournalType.STANDARD,
            List.of(
                JournalLine.debit(JournalLineId.generate(), debitAccount, Money.of("10.00", USD)),
                JournalLine.credit(
                    JournalLineId.generate(), creditAccount, Money.of("10.00", USD))));
    entry.addReference(Reference.payment("pay-1"));
    return entry;
  }

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

  @Test
  void collectsNoIssuesForAWellFormedEntryWithNoEnrichedContext() {
    JournalEntry entry = entry(AccountId.generate(), AccountId.generate());

    ValidationResult result = VALIDATOR.validate(entry, ValidationContext.at(NOW));

    assertThat(result.isValid()).isTrue();
  }

  @Test
  void neverShortCircuitsAndCollectsEveryIssueAtOnce() {
    ChartOfAccounts chart = ChartOfAccounts.open();
    AccountId knownAccount = AccountId.generate();
    chart.add(Account.open(knownAccount, chart.id(), "1000", "Cash", TestAccountType.ASSET_TYPE));
    AccountId unknownAccount = AccountId.generate();

    JournalEntry entry =
        new JournalEntry(
            JournalEntryId.generate(),
            LedgerId.generate(),
            StandardJournalType.STANDARD,
            List.of(
                JournalLine.debit(JournalLineId.generate(), knownAccount, Money.of("10.00", USD)),
                JournalLine.credit(
                    JournalLineId.generate(), unknownAccount, Money.of("10.00", USD))));
    // No reference added, and the period is closed - both should be reported alongside the
    // missing account, in the same pass.
    ValidationContext context =
        ValidationContext.at(NOW).withChartOfAccounts(chart).withPeriod(march().close());

    ValidationResult result = VALIDATOR.validate(entry, context);

    assertThat(result.isValid()).isFalse();
    List<String> codes = result.issues().stream().map(ValidationIssue::ruleCode).toList();
    assertThat(codes).contains("ACCOUNT_NOT_FOUND", "PERIOD_NOT_OPEN", "MISSING_REFERENCE");
  }

  @Test
  void reportsWhenAPostingRuleWasNotMatched() {
    JournalEntry entry = entry(AccountId.generate(), AccountId.generate());
    ValidationContext context = ValidationContext.at(NOW).withPostingRuleMatched(false);

    ValidationResult result = VALIDATOR.validate(entry, context);

    assertThat(result.isValid()).isFalse();
    assertThat(result.issues().stream().map(ValidationIssue::ruleCode))
        .contains("POSTING_RULE_NOT_MATCHED");
  }

  @Test
  void withRulesExtendsTheDefaultSetWithoutReplacingIt() {
    ValidationRule alwaysFails =
        (journalEntry, context) ->
            List.of(
                ValidationIssue.of(
                    "CUSTOM_RULE", "always fails.", io.genfin.api.exception.Severity.ERROR));
    JournalValidator extended = Validators.withRules(List.of(alwaysFails));
    JournalEntry entry = entry(AccountId.generate(), AccountId.generate());

    ValidationResult result = extended.validate(entry, ValidationContext.at(NOW));

    assertThat(result.issues().stream().map(ValidationIssue::ruleCode)).contains("CUSTOM_RULE");
  }
}
