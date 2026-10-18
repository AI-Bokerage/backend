package io.genfin.refund.internal.policy;

import io.genfin.money.money.Money;
import io.genfin.refund.policy.RefundPolicyContext;
import io.genfin.refund.port.policy.ApprovalPolicy;
import java.util.Optional;

/**
 * Requires approval only once {@code context.requestedAmount()} exceeds a configured {@code
 * threshold}. With no threshold configured (the default), approval is never required — callers that
 * need a threshold provide their own {@code Money}.
 */
public final class DefaultApprovalPolicy implements ApprovalPolicy {

  private final Optional<Money> threshold;

  public DefaultApprovalPolicy() {
    this(Optional.empty());
  }

  public DefaultApprovalPolicy(Optional<Money> threshold) {
    this.threshold = threshold;
  }

  @Override
  public boolean requiresApproval(RefundPolicyContext context) {
    return threshold.map(limit -> context.requestedAmount().compareTo(limit) > 0).orElse(false);
  }
}
