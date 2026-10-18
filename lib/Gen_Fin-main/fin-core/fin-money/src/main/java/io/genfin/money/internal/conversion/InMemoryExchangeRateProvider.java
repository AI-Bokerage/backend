package io.genfin.money.internal.conversion;

import io.genfin.money.conversion.ConversionContext;
import io.genfin.money.conversion.ExchangeRate;
import io.genfin.money.currency.Currency;
import io.genfin.money.port.conversion.ExchangeRateProvider;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory rate table — a default for tests and development, not a market-data integration. */
public final class InMemoryExchangeRateProvider implements ExchangeRateProvider {

  private final Map<String, ExchangeRate> rates = new ConcurrentHashMap<>();

  public void put(ExchangeRate rate) {
    rates.put(key(rate.base(), rate.quote()), rate);
  }

  @Override
  public Optional<ExchangeRate> rate(Currency base, Currency quote, ConversionContext context) {
    if (base.equals(quote)) {
      return Optional.of(new ExchangeRate(base, quote, java.math.BigDecimal.ONE, context.asOf()));
    }
    ExchangeRate direct = rates.get(key(base, quote));
    if (direct != null) {
      return Optional.of(direct);
    }
    ExchangeRate inverse = rates.get(key(quote, base));
    return Optional.ofNullable(inverse).map(ExchangeRate::invert);
  }

  private static String key(Currency base, Currency quote) {
    return base.code() + "/" + quote.code();
  }
}
