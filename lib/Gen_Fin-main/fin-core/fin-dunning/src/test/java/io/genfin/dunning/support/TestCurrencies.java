package io.genfin.dunning.support;

import io.genfin.money.currency.Currency;
import io.genfin.money.currency.CurrencyFactory;

public final class TestCurrencies {

  public static final Currency USD =
      CurrencyFactory.newCurrency()
          .code("USD")
          .symbol("$")
          .displayName("US Dollar")
          .fractionDigits(2)
          .build();

  private TestCurrencies() {}
}
