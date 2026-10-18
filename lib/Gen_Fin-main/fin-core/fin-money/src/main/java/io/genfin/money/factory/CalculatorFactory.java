package io.genfin.money.factory;

import io.genfin.money.currency.Currency;
import io.genfin.money.internal.arithmetic.DefaultMoneyCalculator;
import io.genfin.money.port.arithmetic.MoneyCalculator;

/**
 * Builds {@link MoneyCalculator} instances. Consumers must not instantiate calculator
 * implementations directly.
 */
public final class CalculatorFactory {

  private CalculatorFactory() {}

  public static MoneyCalculator forCurrency(Currency currency) {
    return new DefaultMoneyCalculator(currency);
  }
}
