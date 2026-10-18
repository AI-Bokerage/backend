package io.genfin.refund.port.policy;

import io.genfin.api.port.spi.Extension;
import io.genfin.refund.policy.PolicyViolation;
import io.genfin.refund.policy.RefundPolicyContext;
import java.util.Optional;

/** Governs which {@link io.genfin.refund.reason.RefundReason}s are permitted on a refund. */
public interface ReasonPolicy extends Extension {

  /** Empty means {@code context.reason()} is permitted. */
  Optional<PolicyViolation> check(RefundPolicyContext context);
}
