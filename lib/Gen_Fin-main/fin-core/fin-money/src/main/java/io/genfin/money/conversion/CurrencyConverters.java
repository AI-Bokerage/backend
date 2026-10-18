package io.genfin.money.conversion;

import io.genfin.money.internal.conversion.DefaultCurrencyConverter;
import io.genfin.money.internal.conversion.InMemoryExchangeRateProvider;
import io.genfin.money.port.conversion.CurrencyConverter;
import io.genfin.money.port.conversion.ExchangeRateProvider;

/** Factory for {@link CurrencyConverter} and default {@link ExchangeRateProvider} instances. */
public final class CurrencyConverters {

  private CurrencyConverters() {}

  public static CurrencyConverter using(ExchangeRateProvider rateProvider) {
    return new DefaultCurrencyConverter(rateProvider);
  }

  public static InMemoryExchangeRateProvider inMemoryRates() {
    return new InMemoryExchangeRateProvider();
  }
}
