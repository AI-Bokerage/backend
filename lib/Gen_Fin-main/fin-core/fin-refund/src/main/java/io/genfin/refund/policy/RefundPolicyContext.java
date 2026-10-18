package io.genfin.refund.policy;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.refund.reason.RefundReason;
import java.time.Instant;
import java.util.List;

/**
 * Everything a {@link io.genfin.refund.port.policy.RefundPolicy} and its sub-policies need to
 * decide whether a requested refund is permitted: the payment being refunded, what has already been
 * refunded against it, what is now being requested, why, and when.
 */
public record RefundPolicyContext(
    Money paymentTotal,
    List<Money> priorRefunds,
    Money requestedAmount,
    RefundReason reason,
    Instant paymentDate,
    Instant evaluationTime)
    implements ValueObject {

  public RefundPolicyContext {
    Validate.notNull(paymentTotal, "paymentTotal must not be null.");
    priorRefunds = List.copyOf(priorRefunds);
    Validate.notNull(requestedAmount, "requestedAmount must not be null.");
    Validate.notNull(reason, "reason must not be null.");
    Validate.notNull(paymentDate, "paymentDate must not be null.");
    Validate.notNull(evaluationTime, "evaluationTime must not be null.");
  }

  /** Sum of every refund already applied against the payment, plus the amount now requested. */
  public Money totalAfterThisRequest() {
    Money total = requestedAmount;
    for (Money prior : priorRefunds) {
      total = total.add(prior);
    }
    return total;
  }
}
