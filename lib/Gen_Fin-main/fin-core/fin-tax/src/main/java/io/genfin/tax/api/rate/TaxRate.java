package io.genfin.tax.api.rate;

import io.genfin.api.validation.Validate;
import io.genfin.money.percentage.Percentage;
import io.genfin.tax.api.decision.TaxComponentType;

/** The rate configured for one {@link TaxComponentType} (e.g. CGST at 9%). */
public record TaxRate(TaxComponentType component, Percentage rate) {

  public TaxRate {
    Validate.notNull(component, "component must not be null.");
    Validate.notNull(rate, "rate must not be null.");
  }

  public static TaxRate of(TaxComponentType component, Percentage rate) {
    return new TaxRate(component, rate);
  }
}
