package io.genfin.tax.port;

import io.genfin.api.port.spi.Extension;
import io.genfin.tax.api.decision.TaxComponentType;
import io.genfin.tax.api.rate.TaxRate;
import java.util.Optional;

/**
 * Supplies the configured {@link TaxRate} for a {@link TaxComponentType}. Never hardcoded inside a
 * calculator.
 */
public interface TaxRateProvider extends Extension {

  Optional<TaxRate> rateFor(TaxComponentType component);
}
