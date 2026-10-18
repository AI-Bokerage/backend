package io.genfin.reconciliation.tolerance;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import java.math.BigDecimal;

/**
 * The maximum variance still considered a match on the amount dimension, expressed as a fraction of
 * the reference (left-hand) amount rather than a fixed {@link io.genfin.money.money.Money} figure —
 * e.g. {@code 0.02} allows a 2% gap regardless of currency scale.
 */
public record PercentageTolerance(BigDecimal maxVarianceFraction)
    implements Tolerance, ValueObject {

  public PercentageTolerance {
    Validate.notNull(maxVarianceFraction, "maxVarianceFraction must not be null.");
    Validate.required(
        maxVarianceFraction.signum() >= 0, "maxVarianceFraction must not be negative.");
  }

  public static PercentageTolerance of(BigDecimal maxVarianceFraction) {
    return new PercentageTolerance(maxVarianceFraction);
  }

  @Override
  public ToleranceType type() {
    return StandardToleranceType.PERCENTAGE;
  }
}
