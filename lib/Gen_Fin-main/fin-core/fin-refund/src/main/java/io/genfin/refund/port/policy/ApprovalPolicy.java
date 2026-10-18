package io.genfin.refund.port.policy;

import io.genfin.api.port.spi.Extension;
import io.genfin.refund.policy.RefundPolicyContext;

/** Governs whether a refund request must be routed through manual approval before processing. */
public interface ApprovalPolicy extends Extension {

  boolean requiresApproval(RefundPolicyContext context);
}
