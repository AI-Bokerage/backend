package io.genfin.ledger.calculation;

import io.genfin.ledger.internal.calculation.DefaultVarianceCalculator;
import io.genfin.ledger.port.calculation.VarianceCalculator;

/**
 * Provides the standard {@link VarianceCalculator}, mirroring the module's other {@code *s}
 * factories.
 */
public final class VarianceCalculators {

  private static final VarianceCalculator STANDARD = new DefaultVarianceCalculator();

  private VarianceCalculators() {}

  public static VarianceCalculator standard() {
    return STANDARD;
  }
}
