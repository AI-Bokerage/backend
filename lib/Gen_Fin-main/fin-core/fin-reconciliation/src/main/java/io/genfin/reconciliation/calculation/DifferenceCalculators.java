package io.genfin.reconciliation.calculation;

import io.genfin.reconciliation.internal.calculation.DefaultDifferenceCalculator;
import io.genfin.reconciliation.port.calculation.DifferenceCalculator;

/**
 * Provides the standard {@link DifferenceCalculator}, mirroring the module's other {@code *s}
 * registries.
 */
public final class DifferenceCalculators {

  private static final DifferenceCalculator STANDARD = new DefaultDifferenceCalculator();

  private DifferenceCalculators() {}

  public static DifferenceCalculator standard() {
    return STANDARD;
  }
}
