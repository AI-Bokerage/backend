package io.genfin.refund.internal.policy;

import io.genfin.api.validation.Validate;
import io.genfin.refund.exception.RefundErrorCode;
import io.genfin.refund.policy.PolicyViolation;
import io.genfin.refund.policy.RefundPolicyContext;
import io.genfin.refund.port.policy.RefundWindowPolicy;
import java.time.Duration;
import java.util.Optional;

/** Rejects requests once {@code window} has elapsed since {@code context.paymentDate()}. */
public final class DefaultRefundWindowPolicy implements RefundWindowPolicy {

  private final Duration window;

  public DefaultRefundWindowPolicy(Duration window) {
    this.window = Validate.notNull(window, "window must not be null.");
  }

  @Override
  public Optional<PolicyViolation> check(RefundPolicyContext context) {
    if (context.evaluationTime().isAfter(context.paymentDate().plus(window))) {
      return Optional.of(
          new PolicyViolation(
              RefundErrorCode.REFUND_WINDOW_EXPIRED,
              "Refund window of " + window + " since " + context.paymentDate() + " has elapsed."));
    }
    return Optional.empty();
  }
}
