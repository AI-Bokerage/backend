package io.genfin.tax.port;

import io.genfin.money.port.tax.TaxCalculator;
import io.genfin.tax.api.config.IndianTaxConfiguration;
import io.genfin.tax.internal.calculation.DelegatingTaxCalculator;
import io.genfin.tax.internal.calculation.IndianTaxCalculator;

/**
 * Factory assembling Tax Engine V1 into {@code fin-money}'s existing {@link TaxCalculator}
 * extension point — the integration surface applications actually register.
 */
public final class TaxEngines {

  private TaxEngines() {}

  /**
   * {@link #indian()} wrapped in {@link DelegatingTaxCalculator} — the ready-to-register default.
   */
  public static TaxCalculator standard() {
    return delegating(indian());
  }

  /** The Indian engine alone, using {@link IndianTaxConfiguration#standard()}. */
  public static TaxCalculator indian() {
    return indian(IndianTaxConfiguration.standard());
  }

  public static TaxCalculator indian(IndianTaxConfiguration configuration) {
    return new IndianTaxCalculator(
        TaxDecisionResolvers.of(configuration.defaultExportTreatment()),
        TaxRateProviders.of(configuration.rates()));
  }

  public static TaxCalculator delegating(TaxCalculator engine) {
    return new DelegatingTaxCalculator(engine);
  }
}
