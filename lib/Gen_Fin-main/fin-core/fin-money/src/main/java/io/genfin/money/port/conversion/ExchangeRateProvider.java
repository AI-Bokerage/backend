package io.genfin.money.port.conversion;

import io.genfin.api.port.spi.Extension;
import io.genfin.money.conversion.ConversionContext;
import io.genfin.money.conversion.ExchangeRate;
import io.genfin.money.currency.Currency;
import java.util.Optional;

/**
 * Pluggable source of exchange rates. Real integrations (market-data feeds, bank APIs) implement
 * this.
 */
public interface ExchangeRateProvider extends Extension {

  Optional<ExchangeRate> rate(Currency base, Currency quote, ConversionContext context);
}
