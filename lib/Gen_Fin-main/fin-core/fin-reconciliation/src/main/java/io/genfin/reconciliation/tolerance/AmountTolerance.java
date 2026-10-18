package io.genfin.reconciliation.tolerance;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;

/**
 * The maximum absolute {@link Money} variance still considered a match on the amount dimension —
 * e.g. a settlement short by a rounded processing fee.
 */
public record AmountTolerance(Money maxVariance) implements Tolerance, ValueObject {

  public AmountTolerance {
    Validate.notNull(maxVariance, "maxVariance must not be null.");
    Validate.required(!maxVariance.isNegative(), "maxVariance must not be negative.");
  }

  public static AmountTolerance of(Money maxVariance) {
    return new AmountTolerance(maxVariance);
  }

  @Override
  public ToleranceType type() {
    return StandardToleranceType.AMOUNT;
  }
}
