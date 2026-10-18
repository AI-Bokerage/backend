package io.genfin.tax.internal.rate;

import io.genfin.api.validation.Validate;
import io.genfin.money.percentage.Percentage;
import io.genfin.tax.api.config.RateConfiguration;
import io.genfin.tax.api.decision.TaxComponentType;
import io.genfin.tax.api.rate.TaxRate;
import io.genfin.tax.port.TaxRateProvider;
import java.util.Optional;

public final class DefaultTaxRateProvider implements TaxRateProvider {

  private final RateConfiguration configuration;

  public DefaultTaxRateProvider(RateConfiguration configuration) {
    this.configuration = Validate.notNull(configuration, "configuration must not be null.");
  }

  @Override
  public Optional<TaxRate> rateFor(TaxComponentType component) {
    Validate.notNull(component, "component must not be null.");
    Optional<Percentage> rate = configuration.rateFor(component.code());
    return rate.map(percentage -> TaxRate.of(component, percentage));
  }
}
