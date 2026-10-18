package io.genfin.reconciliation.calculation;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;

/**
 * The result of a {@code DifferenceCalculator} comparing two amounts: {@code signed} is {@code left
 * - right} (which side is ahead), {@code absolute} is its magnitude (the size of the gap, what most
 * callers actually want to threshold against).
 */
public record AmountDifference(Money signed, Money absolute) implements ValueObject {

  public AmountDifference {
    Validate.notNull(signed, "signed must not be null.");
    Validate.notNull(absolute, "absolute must not be null.");
  }

  public boolean isZero() {
    return absolute.isZero();
  }
}
