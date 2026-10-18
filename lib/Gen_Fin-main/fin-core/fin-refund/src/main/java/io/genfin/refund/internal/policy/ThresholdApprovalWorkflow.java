package io.genfin.refund.internal.policy;

import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.refund.approval.ApprovalRequirement;
import io.genfin.refund.policy.RefundPolicyContext;
import io.genfin.refund.port.policy.ApprovalWorkflow;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Scales the number of required approvals with the requested amount: the highest configured tier
 * threshold at or below {@code context.requestedAmount()} sets the required approval count. Below
 * the lowest configured threshold, no approval is required.
 */
public final class ThresholdApprovalWorkflow implements ApprovalWorkflow {

  private final NavigableMap<Money, Integer> tiers;

  public ThresholdApprovalWorkflow(NavigableMap<Money, Integer> tiers) {
    Validate.notNull(tiers, "tiers must not be null.");
    Validate.argument(!tiers.isEmpty(), "tiers must not be empty.");
    this.tiers = new TreeMap<>(tiers);
  }

  @Override
  public ApprovalRequirement requirementFor(RefundPolicyContext context) {
    var tier = tiers.floorEntry(context.requestedAmount());
    return new ApprovalRequirement(tier == null ? 0 : tier.getValue());
  }
}
