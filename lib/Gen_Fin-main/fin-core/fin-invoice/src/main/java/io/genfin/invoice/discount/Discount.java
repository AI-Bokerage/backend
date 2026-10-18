package io.genfin.invoice.discount;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.money.percentage.Percentage;

/**
 * A discount applied at invoice or line level. Exactly one of {@code fixedAmount}/{@code
 * percentage} is expected to be non-null — which one is meaningful is left to the {@code
 * DiscountEngine}.
 */
public record Discount(DiscountType type, Money fixedAmount, Percentage percentage, String reason)
    implements ValueObject {

  public Discount {
    Validate.notNull(type, "type must not be null.");
    Validate.argument(
        fixedAmount != null || percentage != null, "either fixedAmount or percentage must be set.");
  }

  public static Discount fixed(Money amount, String reason) {
    return new Discount(StandardDiscountType.FIXED, amount, null, reason);
  }

  public static Discount percentage(Percentage percentage, String reason) {
    return new Discount(StandardDiscountType.PERCENTAGE, null, percentage, reason);
  }

  public static Discount ofType(
      DiscountType type, Money fixedAmount, Percentage percentage, String reason) {
    return new Discount(type, fixedAmount, percentage, reason);
  }
}
