package io.genfin.refund.internal.calculation;

import io.genfin.money.money.Money;
import io.genfin.refund.calculation.RefundAllocation;
import io.genfin.refund.calculation.RefundBalance;
import io.genfin.refund.calculation.RefundCalculationContext;
import io.genfin.refund.calculation.RefundCalculationResult;
import io.genfin.refund.id.RefundId;
import io.genfin.refund.port.calculation.RefundCalculator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class DefaultRefundCalculator implements RefundCalculator {

  @Override
  public RefundBalance calculateBalance(Money paymentTotal, List<Money> refundsApplied) {
    Money totalRefunded = Money.zero(paymentTotal.currency());
    for (Money refund : refundsApplied) {
      totalRefunded = totalRefunded.add(refund);
    }
    return new RefundBalance(paymentTotal, totalRefunded, paymentTotal.subtract(totalRefunded));
  }

  @Override
  public RefundCalculationResult calculate(RefundCalculationContext context) {
    RefundBalance balance = calculateBalance(context.paymentTotal(), context.priorRefunds());
    Money approved = context.requestedAmount().min(balance.refundableRemaining());
    if (approved.isNegative()) {
      approved = Money.zero(context.paymentTotal().currency());
    }
    return new RefundCalculationResult(balance, context.requestedAmount(), approved);
  }

  @Override
  public List<RefundAllocation> allocate(
      Money refundableTotal, Map<RefundId, Money> requestedByRefund) {
    List<RefundAllocation> allocations = new ArrayList<>();
    Money remaining = refundableTotal;
    for (Map.Entry<RefundId, Money> entry : requestedByRefund.entrySet()) {
      Money allocated =
          entry
              .getValue()
              .min(remaining.isNegative() ? Money.zero(refundableTotal.currency()) : remaining);
      allocations.add(new RefundAllocation(entry.getKey(), allocated));
      remaining = remaining.subtract(allocated);
    }
    return allocations;
  }
}
