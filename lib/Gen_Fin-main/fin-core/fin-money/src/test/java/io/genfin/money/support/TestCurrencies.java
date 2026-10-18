package io.genfin.money.support;

import io.genfin.money.currency.Currency;
import io.genfin.money.currency.CurrencyFactory;

/** Deterministic currency fixtures for tests — never depend on the ISO/system currency catalog. */
public final class TestCurrencies {

  public static final Currency USD =
      CurrencyFactory.newCurrency()
          .code("USD")
          .symbol("$")
          .displayName("US Dollar")
          .fractionDigits(2)
          .build();

  public static final Currency EUR =
      CurrencyFactory.newCurrency()
          .code("EUR")
          .symbol("€")
          .displayName("Euro")
          .fractionDigits(2)
          .build();

  public static final Currency JPY =
      CurrencyFactory.newCurrency()
          .code("JPY")
          .symbol("¥")
          .displayName("Japanese Yen")
          .fractionDigits(0)
          .build();

  public static final Currency BHD =
      CurrencyFactory.newCurrency()
          .code("BHD")
          .symbol("BD")
          .displayName("Bahraini Dinar")
          .fractionDigits(3)
          .build();

  private TestCurrencies() {}
}
