package io.genfin.ledger.validation;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.ledger.account.ChartOfAccounts;
import io.genfin.ledger.period.AccountingPeriod;
import java.time.Instant;

/**
 * Everything a {@link ValidationRule} may need beyond the {@link
 * io.genfin.ledger.journal.JournalEntry} itself. Only {@code asOf} is mandatory; every other field
 * is {@code null} unless the caller enriches it, and a rule that needs a field it does not find
 * simply reports no issue - mirrors {@code io.genfin.reconciliation.validation.ValidationContext}.
 */
public record ValidationContext(
    Instant asOf,
    ChartOfAccounts chartOfAccounts,
    AccountingPeriod period,
    Boolean postingRuleMatched)
    implements ValueObject {

  public ValidationContext {
    Validate.notNull(asOf, "asOf must not be null.");
  }

  public static ValidationContext at(Instant asOf) {
    return new ValidationContext(asOf, null, null, null);
  }

  public ValidationContext withChartOfAccounts(ChartOfAccounts chartOfAccounts) {
    return new ValidationContext(asOf, chartOfAccounts, period, postingRuleMatched);
  }

  public ValidationContext withPeriod(AccountingPeriod period) {
    return new ValidationContext(asOf, chartOfAccounts, period, postingRuleMatched);
  }

  public ValidationContext withPostingRuleMatched(boolean postingRuleMatched) {
    return new ValidationContext(asOf, chartOfAccounts, period, postingRuleMatched);
  }
}
