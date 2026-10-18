package io.genfin.payment.port.calculation;

import io.genfin.api.port.spi.Extension;
import io.genfin.money.money.Money;
import io.genfin.payment.calculation.PaymentAllocation;
import io.genfin.payment.calculation.PaymentBalance;
import io.genfin.payment.calculation.SettlementResult;
import io.genfin.payment.id.PaymentId;
import java.util.List;
import java.util.Map;

public interface PaymentCalculator extends Extension {

  PaymentBalance calculateBalance(Money totalDue, List<Money> paymentsApplied);

  SettlementResult settle(Money capturedAmount, Money fees);

  /**
   * Allocates {@code totalDue} across payments in {@code capturedByPayment}'s iteration order — use
   * a {@code LinkedHashMap} when allocation priority matters.
   */
  List<PaymentAllocation> allocate(Money totalDue, Map<PaymentId, Money> capturedByPayment);
}
