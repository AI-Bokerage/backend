package io.genfin.ledger.balance;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.event.OccurredAt;
import io.genfin.api.validation.Validate;
import io.genfin.ledger.id.AccountId;
import io.genfin.ledger.id.AccountingPeriodId;
import io.genfin.money.money.Money;

/**
 * The signed, classification-normalized balance an {@link AccountId} carries out of an {@link
 * AccountingPeriodId} - an {@link OpeningBalance} plus that period's net movement. Becomes the next
 * period's {@link OpeningBalance}.
 */
public record ClosingBalance(
    AccountId accountId, AccountingPeriodId periodId, Money amount, OccurredAt asOf)
    implements ValueObject {

  public ClosingBalance {
    Validate.notNull(accountId, "accountId must not be null.");
    Validate.notNull(periodId, "periodId must not be null.");
    Validate.notNull(amount, "amount must not be null.");
    Validate.notNull(asOf, "asOf must not be null.");
  }

  /** The {@link OpeningBalance} the next period should start from, carrying this amount forward. */
  public OpeningBalance carryForward(AccountingPeriodId nextPeriodId, OccurredAt nextAsOf) {
    return new OpeningBalance(accountId, nextPeriodId, amount, nextAsOf);
  }
}
