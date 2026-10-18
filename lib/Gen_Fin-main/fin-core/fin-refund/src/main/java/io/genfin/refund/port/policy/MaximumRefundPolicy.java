package io.genfin.refund.port.policy;

import io.genfin.api.port.spi.Extension;
import io.genfin.money.money.Money;
import io.genfin.refund.exception.RefundErrorCode;
import io.genfin.refund.policy.PolicyViolation;
import io.genfin.refund.policy.RefundPolicyContext;
import java.util.Optional;

/** Governs the maximum amount refundable against a payment, however that cap is derived. */
public interface MaximumRefundPolicy extends Extension {

  /** The most that may ever be refunded in aggregate against {@code context.paymentTotal()}. */
  Money maximumRefundable(RefundPolicyContext context);

  /** Empty means the request keeps the running total within {@link #maximumRefundable}. */
  default Optional<PolicyViolation> check(RefundPolicyContext context) {
    Money max = maximumRefundable(context);
    if (context.totalAfterThisRequest().compareTo(max) > 0) {
      return Optional.of(
          new PolicyViolation(
              RefundErrorCode.REFUND_EXCEEDS_REFUNDABLE_BALANCE,
              "Total refunded "
                  + context.totalAfterThisRequest()
                  + " would exceed maximum "
                  + max
                  + "."));
    }
    return Optional.empty();
  }
}
