package io.genfin.refund.calculation;

import io.genfin.api.domain.ValueObject;
import io.genfin.money.money.Money;
import java.util.List;

/**
 * Input to a refund calculation: the payment's total, every refund already applied against it, and
 * the amount now being requested (full or partial).
 */
public record RefundCalculationContext(
    Money paymentTotal, List<Money> priorRefunds, Money requestedAmount) implements ValueObject {

  public RefundCalculationContext {
    priorRefunds = List.copyOf(priorRefunds);
  }
}
