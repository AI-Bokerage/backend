package io.genfin.refund.policy;

import io.genfin.refund.internal.policy.DefaultRefundPolicy;
import io.genfin.refund.port.policy.ApprovalPolicy;
import io.genfin.refund.port.policy.MaximumRefundPolicy;
import io.genfin.refund.port.policy.ReasonPolicy;
import io.genfin.refund.port.policy.RefundPolicy;
import io.genfin.refund.port.policy.RefundWindowPolicy;
import io.genfin.refund.port.reason.RefundReasonRegistry;

/**
 * Builds a {@link RefundPolicy} from individually overridable sub-policies, defaulting any not set
 * to {@link RefundPolicies}' defaults. Preferred over calling {@link DefaultRefundPolicy}'s
 * constructor directly.
 */
public final class RefundPolicyBuilder {

  private final RefundReasonRegistry reasonRegistry;
  private RefundWindowPolicy windowPolicy;
  private MaximumRefundPolicy maximumRefundPolicy;
  private ReasonPolicy reasonPolicy;
  private ApprovalPolicy approvalPolicy;

  private RefundPolicyBuilder(RefundReasonRegistry reasonRegistry) {
    this.reasonRegistry = reasonRegistry;
  }

  /** {@code reasonRegistry} backs the default {@link ReasonPolicy}, if none is set explicitly. */
  public static RefundPolicyBuilder newPolicy(RefundReasonRegistry reasonRegistry) {
    return new RefundPolicyBuilder(reasonRegistry);
  }

  public RefundPolicyBuilder windowPolicy(RefundWindowPolicy windowPolicy) {
    this.windowPolicy = windowPolicy;
    return this;
  }

  public RefundPolicyBuilder maximumRefundPolicy(MaximumRefundPolicy maximumRefundPolicy) {
    this.maximumRefundPolicy = maximumRefundPolicy;
    return this;
  }

  public RefundPolicyBuilder reasonPolicy(ReasonPolicy reasonPolicy) {
    this.reasonPolicy = reasonPolicy;
    return this;
  }

  public RefundPolicyBuilder approvalPolicy(ApprovalPolicy approvalPolicy) {
    this.approvalPolicy = approvalPolicy;
    return this;
  }

  public RefundPolicy build() {
    return new DefaultRefundPolicy(
        windowPolicy == null ? RefundPolicies.defaultWindowPolicy() : windowPolicy,
        maximumRefundPolicy == null
            ? RefundPolicies.defaultMaximumRefundPolicy()
            : maximumRefundPolicy,
        reasonPolicy == null ? RefundPolicies.reasonPolicy(reasonRegistry) : reasonPolicy,
        approvalPolicy == null ? RefundPolicies.defaultApprovalPolicy() : approvalPolicy);
  }
}
