package io.genfin.payment.calculation;

import io.genfin.api.domain.ValueObject;
import io.genfin.money.money.Money;

/**
 * {@code remaining = totalDue - totalPaid}: positive means underpaid, negative means overpaid, zero
 * means settled.
 */
public record PaymentBalance(Money totalDue, Money totalPaid, Money remaining)
    implements ValueObject {

  public boolean isSettled() {
    return remaining.isZero();
  }

  public boolean isUnderpaid() {
    return remaining.isPositive();
  }

  public boolean isOverpaid() {
    return remaining.isNegative();
  }
}
