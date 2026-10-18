package io.genfin.reconciliation.calculation;

import io.genfin.reconciliation.internal.calculation.DefaultSummaryCalculator;
import io.genfin.reconciliation.port.calculation.SummaryCalculator;

/**
 * Provides the standard {@link SummaryCalculator}, mirroring the module's other {@code *s}
 * registries.
 */
public final class SummaryCalculators {

  private static final SummaryCalculator STANDARD = new DefaultSummaryCalculator();

  private SummaryCalculators() {}

  public static SummaryCalculator standard() {
    return STANDARD;
  }
}
