package io.genfin.refund.calculation;

import io.genfin.api.domain.ValueObject;
import io.genfin.money.money.Money;

/**
 * Outcome of a refund calculation: the balance as it stood before this request, what was requested,
 * and what was actually approved — {@code approvedAmount} is clamped to the refundable remainder,
 * so it is always {@code <= requestedAmount} (over-refund prevention).
 */
public record RefundCalculationResult(
    RefundBalance balanceBeforeRequest, Money requestedAmount, Money approvedAmount)
    implements ValueObject {

  /** True when the requested amount had to be reduced to avoid exceeding the refundable balance. */
  public boolean wasCapped() {
    return approvedAmount.compareTo(requestedAmount) < 0;
  }

  /** True when this request refunds the entire remaining refundable balance. */
  public boolean isFullRefund() {
    return approvedAmount.compareTo(balanceBeforeRequest.refundableRemaining()) == 0
        && approvedAmount.isPositive();
  }
}
