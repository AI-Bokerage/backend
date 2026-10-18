package io.genfin.money.tax;

import io.genfin.money.internal.tax.NoOpTaxCalculator;
import io.genfin.money.port.tax.TaxCalculator;

/**
 * Factory for built-in {@link TaxCalculator}s. Jurisdiction-specific calculators come from Phase 4,
 * not here.
 */
public final class TaxCalculators {

  private static final TaxCalculator NO_OP = new NoOpTaxCalculator();

  private TaxCalculators() {}

  public static TaxCalculator noOp() {
    return NO_OP;
  }
}
