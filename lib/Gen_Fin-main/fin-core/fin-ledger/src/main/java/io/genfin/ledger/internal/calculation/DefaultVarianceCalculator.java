package io.genfin.ledger.internal.calculation;

import io.genfin.api.validation.Validate;
import io.genfin.ledger.port.calculation.VarianceCalculator;
import io.genfin.money.money.Money;
import io.genfin.money.percentage.Percentage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * The standard {@link VarianceCalculator}: {@code (actual - base) / base}, computed to a fixed
 * internal scale - the only place in this calculator that touches {@link BigDecimal} directly.
 */
public final class DefaultVarianceCalculator implements VarianceCalculator {

  private static final int SCALE = 10;

  @Override
  public Optional<Percentage> varianceOf(Money base, Money actual) {
    Validate.notNull(base, "base must not be null.");
    Validate.notNull(actual, "actual must not be null.");
    Validate.required(
        base.currency().equals(actual.currency()), "base and actual must share a currency.");
    if (base.isZero()) {
      return Optional.empty();
    }
    Money gap = actual.subtract(base);
    BigDecimal fraction = gap.amount().divide(base.amount(), SCALE, RoundingMode.HALF_UP);
    return Optional.of(Percentage.ofFraction(fraction));
  }
}
