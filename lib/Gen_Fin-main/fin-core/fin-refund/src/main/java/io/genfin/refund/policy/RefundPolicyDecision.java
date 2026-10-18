package io.genfin.refund.policy;

import io.genfin.api.domain.ValueObject;
import java.util.List;

/**
 * Outcome of evaluating a {@link io.genfin.refund.port.policy.RefundPolicy}: whether the request is
 * permitted at all, and — independently — whether it must be routed through approval before
 * processing.
 */
public record RefundPolicyDecision(boolean approvalRequired, List<PolicyViolation> violations)
    implements ValueObject {

  public RefundPolicyDecision {
    violations = List.copyOf(violations);
  }

  public boolean isPermitted() {
    return violations.isEmpty();
  }
}
