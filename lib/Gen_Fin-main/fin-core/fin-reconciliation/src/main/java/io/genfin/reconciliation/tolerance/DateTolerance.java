package io.genfin.reconciliation.tolerance;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import java.time.Duration;

/**
 * The maximum timing drift still considered a match on the date dimension — e.g. a bank posting
 * that lands a day after the provider settlement.
 */
public record DateTolerance(Duration maxVariance) implements Tolerance, ValueObject {

  public DateTolerance {
    Validate.notNull(maxVariance, "maxVariance must not be null.");
    Validate.required(!maxVariance.isNegative(), "maxVariance must not be negative.");
  }

  public static DateTolerance of(Duration maxVariance) {
    return new DateTolerance(maxVariance);
  }

  @Override
  public ToleranceType type() {
    return StandardToleranceType.DATE;
  }
}
