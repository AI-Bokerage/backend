package io.genfin.refund.approval;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;

/**
 * Number of independent approvals a refund request must collect, per an {@link
 * io.genfin.refund.port.policy.ApprovalWorkflow}, before it may proceed.
 */
public record ApprovalRequirement(int requiredApprovals) implements ValueObject {

  /** No human approval needed: the workflow is satisfied automatically. */
  public static final ApprovalRequirement NONE = new ApprovalRequirement(0);

  public ApprovalRequirement {
    Validate.nonNegative(requiredApprovals, "requiredApprovals must not be negative.");
  }

  public boolean isAutomatic() {
    return requiredApprovals == 0;
  }

  public boolean isSatisfiedBy(int distinctApprovals) {
    return distinctApprovals >= requiredApprovals;
  }
}
