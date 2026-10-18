package io.genfin.invoice.internal.discount;

import io.genfin.invoice.discount.Discount;
import io.genfin.invoice.port.discount.DiscountEngine;
import io.genfin.money.money.Money;
import io.genfin.money.percentage.PercentageCalculator;

public final class DefaultDiscountEngine implements DiscountEngine {

  @Override
  public Money apply(Discount discount, Money baseAmount) {
    if (discount.percentage() != null) {
      return PercentageCalculator.apply(baseAmount, discount.percentage());
    }
    return discount.fixedAmount().min(baseAmount);
  }
}
