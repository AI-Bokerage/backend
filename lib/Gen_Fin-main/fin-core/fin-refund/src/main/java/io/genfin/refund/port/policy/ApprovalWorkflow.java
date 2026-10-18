package io.genfin.refund.port.policy;

import io.genfin.api.port.spi.Extension;
import io.genfin.refund.approval.ApprovalRequirement;
import io.genfin.refund.policy.RefundPolicyContext;

/**
 * Determines how many independent approvals a refund request needs, once {@link ApprovalPolicy} has
 * decided that approval is required at all. Kept separate from {@link ApprovalPolicy} so "must this
 * be approved?" and "what does approving it require?" can be configured independently.
 */
public interface ApprovalWorkflow extends Extension {

  ApprovalRequirement requirementFor(RefundPolicyContext context);
}
