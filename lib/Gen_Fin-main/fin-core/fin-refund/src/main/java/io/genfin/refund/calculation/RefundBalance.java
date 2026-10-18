package io.genfin.refund.calculation;

import io.genfin.api.domain.ValueObject;
import io.genfin.money.money.Money;

/**
 * {@code remaining = paymentTotal - totalRefunded}: positive means refundable amount is left, zero
 * means fully refunded, negative would mean over-refunded (prevented upstream by {@link
 * io.genfin.refund.port.calculation.RefundCalculator}).
 */
public record RefundBalance(Money paymentTotal, Money totalRefunded, Money remaining)
    implements ValueObject {

  public boolean isFullyRefunded() {
    return remaining.isZero();
  }

  public boolean isPartiallyRefunded() {
    return totalRefunded.isPositive() && remaining.isPositive();
  }

  public boolean isOverRefunded() {
    return remaining.isNegative();
  }

  /** The refundable amount still available — clamped to zero, never negative. */
  public Money refundableRemaining() {
    return isOverRefunded() ? Money.zero(remaining.currency()) : remaining;
  }
}
