package io.genfin.invoice.factory;

import io.genfin.invoice.discount.Discount;
import io.genfin.invoice.discount.StandardDiscountType;
import io.genfin.money.money.Money;
import io.genfin.money.percentage.Percentage;

public final class DiscountFactory {

  private DiscountFactory() {}

  public static Discount fixed(Money amount, String reason) {
    return Discount.fixed(amount, reason);
  }

  public static Discount percentage(Percentage percentage, String reason) {
    return Discount.percentage(percentage, reason);
  }

  public static Discount coupon(Money amount, String code) {
    return Discount.ofType(StandardDiscountType.COUPON, amount, null, code);
  }

  public static Discount promotional(Percentage percentage, String reason) {
    return Discount.ofType(StandardDiscountType.PROMOTIONAL, null, percentage, reason);
  }

  public static Discount manual(Money amount, String reason) {
    return Discount.ofType(StandardDiscountType.MANUAL, amount, null, reason);
  }
}
