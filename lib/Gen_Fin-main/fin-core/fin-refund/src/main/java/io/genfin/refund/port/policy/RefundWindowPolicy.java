package io.genfin.refund.port.policy;

import io.genfin.api.port.spi.Extension;
import io.genfin.refund.policy.PolicyViolation;
import io.genfin.refund.policy.RefundPolicyContext;
import java.util.Optional;

/** Governs how long after a payment a refund may still be requested against it. */
public interface RefundWindowPolicy extends Extension {

  /** Empty means the request falls within the allowed refund window. */
  Optional<PolicyViolation> check(RefundPolicyContext context);
}
