package io.genfin.money.percentage;

import io.genfin.money.money.Money;

/**
 * Applies a {@link Percentage} to a {@link Money} amount using that Money's own arithmetic policy.
 */
public final class PercentageCalculator {

  private PercentageCalculator() {}

  public static Money apply(Money amount, Percentage percentage) {
    return amount.multiply(percentage.fraction());
  }
}
