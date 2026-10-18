package io.genfin.money.percentage;

import io.genfin.money.money.Money;

/** {@code cost + cost * markup} */
public final class MarkupCalculator {

  private MarkupCalculator() {}

  public static Money apply(Money cost, Percentage markup) {
    Money increase = PercentageCalculator.apply(cost, markup);
    return cost.add(increase);
  }
}
