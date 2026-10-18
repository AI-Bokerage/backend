package io.genfin.refund.internal.policy;

import io.genfin.refund.approval.ApprovalRequirement;
import io.genfin.refund.policy.RefundPolicyContext;
import io.genfin.refund.port.policy.ApprovalWorkflow;

/**
 * Requires the same fixed number of approvals for every request. Backs the Automatic (0), Manual /
 * Single (1) and Two-Person (2) approval models — they differ only in the configured count.
 */
public final class FixedApprovalWorkflow implements ApprovalWorkflow {

  private final ApprovalRequirement requirement;

  public FixedApprovalWorkflow(int requiredApprovals) {
    this.requirement = new ApprovalRequirement(requiredApprovals);
  }

  @Override
  public ApprovalRequirement requirementFor(RefundPolicyContext context) {
    return requirement;
  }
}
