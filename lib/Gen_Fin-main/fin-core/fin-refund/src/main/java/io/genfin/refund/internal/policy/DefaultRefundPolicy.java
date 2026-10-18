package io.genfin.refund.internal.policy;

import io.genfin.api.validation.Validate;
import io.genfin.refund.policy.PolicyViolation;
import io.genfin.refund.policy.RefundPolicyContext;
import io.genfin.refund.policy.RefundPolicyDecision;
import io.genfin.refund.port.policy.ApprovalPolicy;
import io.genfin.refund.port.policy.MaximumRefundPolicy;
import io.genfin.refund.port.policy.ReasonPolicy;
import io.genfin.refund.port.policy.RefundPolicy;
import io.genfin.refund.port.policy.RefundWindowPolicy;
import java.util.ArrayList;
import java.util.List;

/** Composes the four sub-policies into one {@link RefundPolicyDecision}. */
public final class DefaultRefundPolicy implements RefundPolicy {

  private final RefundWindowPolicy windowPolicy;
  private final MaximumRefundPolicy maximumRefundPolicy;
  private final ReasonPolicy reasonPolicy;
  private final ApprovalPolicy approvalPolicy;

  public DefaultRefundPolicy(
      RefundWindowPolicy windowPolicy,
      MaximumRefundPolicy maximumRefundPolicy,
      ReasonPolicy reasonPolicy,
      ApprovalPolicy approvalPolicy) {
    this.windowPolicy = Validate.notNull(windowPolicy, "windowPolicy must not be null.");
    this.maximumRefundPolicy =
        Validate.notNull(maximumRefundPolicy, "maximumRefundPolicy must not be null.");
    this.reasonPolicy = Validate.notNull(reasonPolicy, "reasonPolicy must not be null.");
    this.approvalPolicy = Validate.notNull(approvalPolicy, "approvalPolicy must not be null.");
  }

  @Override
  public RefundPolicyDecision evaluate(RefundPolicyContext context) {
    List<PolicyViolation> violations = new ArrayList<>();
    windowPolicy.check(context).ifPresent(violations::add);
    maximumRefundPolicy.check(context).ifPresent(violations::add);
    reasonPolicy.check(context).ifPresent(violations::add);
    return new RefundPolicyDecision(approvalPolicy.requiresApproval(context), violations);
  }
}
