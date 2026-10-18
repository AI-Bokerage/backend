package io.genfin.ledger.trialbalance;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;

/**
 * The roll-up of every {@link TrialBalanceEntry} in a {@link TrialBalance}: how many accounts were
 * touched and the grand total of each side. A well-formed trial balance always has {@code
 * totalDebit} equal to {@code totalCredit} - {@link TrialBalanceValidator} is what checks that.
 */
public record TrialBalanceSummary(int accountCount, Money totalDebit, Money totalCredit)
    implements ValueObject {

  public TrialBalanceSummary {
    Validate.nonNegative(accountCount, "accountCount must not be negative.");
    Validate.notNull(totalDebit, "totalDebit must not be null.");
    Validate.notNull(totalCredit, "totalCredit must not be null.");
  }
}
