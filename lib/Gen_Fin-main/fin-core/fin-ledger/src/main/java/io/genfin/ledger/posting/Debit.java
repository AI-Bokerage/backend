package io.genfin.ledger.posting;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;

/**
 * A positive amount posted to the debit side of an account. A {@code
 * io.genfin.ledger.port.posting.PostingStrategy} builds one of these (or a {@link Credit}) per
 * account it touches, then hands both to {@link PostingEntry#of(io.genfin.ledger.id.AccountId,
 * Debit)} to keep which side an amount posts to explicit at the call site rather than a raw
 * boolean.
 */
public record Debit(Money amount) implements ValueObject {

  public Debit {
    Validate.notNull(amount, "amount must not be null.");
    Validate.argument(amount.isPositive(), "a debit amount must be positive.");
  }

  public static Debit of(Money amount) {
    return new Debit(amount);
  }
}
