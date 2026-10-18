package io.genfin.refund.port.calculation;

import io.genfin.api.port.spi.Extension;
import io.genfin.money.money.Money;
import io.genfin.refund.calculation.RefundAllocation;
import io.genfin.refund.calculation.RefundBalance;
import io.genfin.refund.calculation.RefundCalculationContext;
import io.genfin.refund.calculation.RefundCalculationResult;
import io.genfin.refund.id.RefundId;
import java.util.List;
import java.util.Map;

public interface RefundCalculator extends Extension {

  RefundBalance calculateBalance(Money paymentTotal, List<Money> refundsApplied);

  /**
   * Evaluates a refund request (full or partial) against every refund already applied to the
   * payment, clamping the approved amount to the remaining refundable balance.
   */
  RefundCalculationResult calculate(RefundCalculationContext context);

  /**
   * Allocates {@code refundableTotal} across simultaneous refund requests in {@code
   * requestedByRefund}'s iteration order — use a {@code LinkedHashMap} when allocation priority
   * matters. Never allocates more than {@code refundableTotal} in aggregate.
   */
  List<RefundAllocation> allocate(Money refundableTotal, Map<RefundId, Money> requestedByRefund);
}
