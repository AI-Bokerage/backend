package io.genfin.refund.internal.policy;

import io.genfin.api.validation.Validate;
import io.genfin.refund.exception.RefundErrorCode;
import io.genfin.refund.policy.PolicyViolation;
import io.genfin.refund.policy.RefundPolicyContext;
import io.genfin.refund.port.policy.ReasonPolicy;
import io.genfin.refund.port.reason.RefundReasonRegistry;
import java.util.Optional;

/** Permits exactly the reasons registered in the given {@link RefundReasonRegistry}. */
public final class DefaultReasonPolicy implements ReasonPolicy {

  private final RefundReasonRegistry registry;

  public DefaultReasonPolicy(RefundReasonRegistry registry) {
    this.registry = Validate.notNull(registry, "registry must not be null.");
  }

  @Override
  public Optional<PolicyViolation> check(RefundPolicyContext context) {
    if (registry.find(context.reason()).isEmpty()) {
      return Optional.of(
          new PolicyViolation(
              RefundErrorCode.INVALID_REFUND_REASON,
              "Refund reason " + context.reason().code() + " is not permitted."));
    }
    return Optional.empty();
  }
}
