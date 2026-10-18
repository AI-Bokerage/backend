package io.genfin.money.percentage;

import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import java.math.BigDecimal;

/**
 * Given a cost and a target margin (as a fraction of the *selling price*), computes {@code price =
 * cost / (1 - margin)}.
 */
public final class MarginCalculator {

  private MarginCalculator() {}

  public static Money priceForMargin(Money cost, Percentage margin) {
    BigDecimal denominator = BigDecimal.ONE.subtract(margin.fraction());
    Validate.argument(denominator.signum() > 0, "margin must be less than 100%.");
    return cost.divide(denominator);
  }
}
