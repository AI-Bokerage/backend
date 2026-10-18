package io.genfin.payment.internal.calculation;

import io.genfin.money.money.Money;
import io.genfin.payment.calculation.PaymentAllocation;
import io.genfin.payment.calculation.PaymentBalance;
import io.genfin.payment.calculation.SettlementResult;
import io.genfin.payment.id.PaymentId;
import io.genfin.payment.port.calculation.PaymentCalculator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class DefaultPaymentCalculator implements PaymentCalculator {

  @Override
  public PaymentBalance calculateBalance(Money totalDue, List<Money> paymentsApplied) {
    Money totalPaid = Money.zero(totalDue.currency());
    for (Money payment : paymentsApplied) {
      totalPaid = totalPaid.add(payment);
    }
    return new PaymentBalance(totalDue, totalPaid, totalDue.subtract(totalPaid));
  }

  @Override
  public SettlementResult settle(Money capturedAmount, Money fees) {
    return new SettlementResult(capturedAmount, fees, capturedAmount.subtract(fees));
  }

  @Override
  public List<PaymentAllocation> allocate(Money totalDue, Map<PaymentId, Money> capturedByPayment) {
    List<PaymentAllocation> allocations = new ArrayList<>();
    Money remaining = totalDue;
    for (Map.Entry<PaymentId, Money> entry : capturedByPayment.entrySet()) {
      Money allocated =
          entry
              .getValue()
              .min(remaining.isNegative() ? Money.zero(totalDue.currency()) : remaining);
      allocations.add(new PaymentAllocation(entry.getKey(), allocated));
      remaining = remaining.subtract(allocated);
    }
    return allocations;
  }
}
