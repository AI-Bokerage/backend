package io.genfin.refund.internal.policy;

import io.genfin.money.money.Money;
import io.genfin.refund.policy.RefundPolicyContext;
import io.genfin.refund.port.policy.MaximumRefundPolicy;
import java.util.Optional;

/**
 * Caps refunds at {@code context.paymentTotal()} unless a stricter absolute {@code cap} is
 * configured, in which case the lower of the two applies.
 */
public final class DefaultMaximumRefundPolicy implements MaximumRefundPolicy {

  private final Optional<Money> cap;

  public DefaultMaximumRefundPolicy() {
    this(Optional.empty());
  }

  public DefaultMaximumRefundPolicy(Optional<Money> cap) {
    this.cap = cap;
  }

  @Override
  public Money maximumRefundable(RefundPolicyContext context) {
    return cap.map(context.paymentTotal()::min).orElse(context.paymentTotal());
  }
}
