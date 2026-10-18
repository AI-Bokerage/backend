package io.genfin.ledger.posting;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;

/**
 * A positive amount posted to the credit side of an account - the counterpart to {@link Debit}, see
 * there for how the two are used together.
 */
public record Credit(Money amount) implements ValueObject {

  public Credit {
    Validate.notNull(amount, "amount must not be null.");
    Validate.argument(amount.isPositive(), "a credit amount must be positive.");
  }

  public static Credit of(Money amount) {
    return new Credit(amount);
  }
}
