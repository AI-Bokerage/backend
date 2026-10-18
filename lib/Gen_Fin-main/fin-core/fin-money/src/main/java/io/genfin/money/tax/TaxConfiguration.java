package io.genfin.money.tax;

import io.genfin.money.port.rounding.RoundingStrategy;
import io.genfin.money.rounding.RoundingStrategies;

/** Framework-level tax switches — never a jurisdiction's rates or formulas. */
public record TaxConfiguration(boolean taxEnabled, RoundingStrategy componentRounding) {

  public static TaxConfiguration disabled() {
    return new TaxConfiguration(false, RoundingStrategies.HALF_UP);
  }

  public static TaxConfiguration enabled(RoundingStrategy componentRounding) {
    return new TaxConfiguration(true, componentRounding);
  }
}
