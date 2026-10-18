package io.genfin.money.percentage;

import io.genfin.money.money.Money;

/** {@code price - price * discount} */
public final class DiscountCalculator {

  private DiscountCalculator() {}

  public static Money apply(Money price, Percentage discount) {
    Money reduction = PercentageCalculator.apply(price, discount);
    return price.subtract(reduction);
  }
}
