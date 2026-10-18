package io.genfin.tax.port;

import io.genfin.tax.api.config.RateConfiguration;
import io.genfin.tax.internal.rate.DefaultTaxRateProvider;

/** Factory for a standard, ready-to-use {@link TaxRateProvider}. */
public final class TaxRateProviders {

  private TaxRateProviders() {}

  /** Backed by {@link RateConfiguration#defaultIndianRates()}. */
  public static TaxRateProvider standard() {
    return of(RateConfiguration.defaultIndianRates());
  }

  public static TaxRateProvider of(RateConfiguration configuration) {
    return new DefaultTaxRateProvider(configuration);
  }
}
