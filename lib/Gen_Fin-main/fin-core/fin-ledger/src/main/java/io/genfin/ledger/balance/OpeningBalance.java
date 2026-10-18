package io.genfin.ledger.balance;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.event.OccurredAt;
import io.genfin.api.validation.Validate;
import io.genfin.ledger.id.AccountId;
import io.genfin.ledger.id.AccountingPeriodId;
import io.genfin.money.money.Money;

/**
 * The signed, classification-normalized balance an {@link AccountId} carries into an {@link
 * AccountingPeriodId} - the closing balance of whatever period preceded it, or zero for the first
 * period an account participates in.
 */
public record OpeningBalance(
    AccountId accountId, AccountingPeriodId periodId, Money amount, OccurredAt asOf)
    implements ValueObject {

  public OpeningBalance {
    Validate.notNull(accountId, "accountId must not be null.");
    Validate.notNull(periodId, "periodId must not be null.");
    Validate.notNull(amount, "amount must not be null.");
    Validate.notNull(asOf, "asOf must not be null.");
  }
}
