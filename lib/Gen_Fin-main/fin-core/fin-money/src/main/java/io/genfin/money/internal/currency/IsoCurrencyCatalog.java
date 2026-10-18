package io.genfin.money.internal.currency;

import io.genfin.money.currency.Currency;
import io.genfin.money.currency.CurrencyFactory;
import io.genfin.money.port.currency.CurrencyProvider;
import java.util.List;

/**
 * Seeds ISO-4217 currencies from the JDK's {@link java.util.Currency} catalog. This is the ONE
 * place {@code java.util.Currency} is touched — it never appears in any public Gen-Fin API.
 */
public final class IsoCurrencyCatalog implements CurrencyProvider {

  @Override
  public List<Currency> provide() {
    return java.util.Currency.getAvailableCurrencies().stream()
        .map(
            jdk ->
                CurrencyFactory.newCurrency()
                    .code(jdk.getCurrencyCode())
                    .numericCode(jdk.getNumericCode())
                    .symbol(jdk.getSymbol())
                    .displayName(jdk.getDisplayName())
                    .fractionDigits(Math.max(jdk.getDefaultFractionDigits(), 0))
                    .active(true)
                    .build())
        .toList();
  }
}
