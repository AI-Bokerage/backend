package io.genfin.ledger.period;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.ledger.internal.period.DefaultPeriodCalculator;
import io.genfin.ledger.internal.period.DefaultPeriodPolicy;
import io.genfin.ledger.port.period.PeriodCalculator;
import io.genfin.ledger.port.period.PeriodPolicy;

/** Factory for {@link PeriodCalculator} instances. */
public final class PeriodCalculators {

  private static final int JANUARY = 1;
  private static final PeriodPolicy STANDARD_POLICY = new DefaultPeriodPolicy(JANUARY);

  private PeriodCalculators() {}

  /** A January-start calendar-year fiscal policy with ordinary quarters and months. */
  public static PeriodPolicy standardPolicy() {
    return STANDARD_POLICY;
  }

  /** A fiscal policy whose year starts on {@code fiscalYearStartMonth} (1 = January). */
  public static PeriodPolicy calendarPolicy(int fiscalYearStartMonth) {
    return new DefaultPeriodPolicy(fiscalYearStartMonth);
  }

  public static PeriodCalculator of(PeriodPolicy policy) {
    return new DefaultPeriodCalculator(policy);
  }

  /**
   * Resolves the {@link PeriodCalculator} registered in {@code registry} if present; otherwise
   * builds one from the registered {@link PeriodPolicy}, falling back to {@link #standardPolicy()}
   * when none is registered.
   */
  public static PeriodCalculator from(ExtensionRegistry registry) {
    return registry
        .find(PeriodCalculator.class)
        .orElseGet(
            () ->
                of(registry.find(PeriodPolicy.class).orElseGet(PeriodCalculators::standardPolicy)));
  }
}
