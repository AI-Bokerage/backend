package io.genfin.reconciliation.calculation;

import io.genfin.reconciliation.internal.calculation.DefaultVarianceCalculator;
import io.genfin.reconciliation.port.calculation.VarianceCalculator;

/**
 * Provides the standard {@link VarianceCalculator}, mirroring the module's other {@code *s}
 * registries.
 */
public final class VarianceCalculators {

  private static final VarianceCalculator STANDARD = new DefaultVarianceCalculator();

  private VarianceCalculators() {}

  public static VarianceCalculator standard() {
    return STANDARD;
  }
}
