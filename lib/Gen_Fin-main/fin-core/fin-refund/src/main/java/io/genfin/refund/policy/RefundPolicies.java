package io.genfin.refund.policy;

import io.genfin.money.money.Money;
import io.genfin.refund.internal.policy.DefaultApprovalPolicy;
import io.genfin.refund.internal.policy.DefaultMaximumRefundPolicy;
import io.genfin.refund.internal.policy.DefaultReasonPolicy;
import io.genfin.refund.internal.policy.DefaultRefundPolicy;
import io.genfin.refund.internal.policy.DefaultRefundWindowPolicy;
import io.genfin.refund.internal.policy.FixedApprovalWorkflow;
import io.genfin.refund.internal.policy.ThresholdApprovalWorkflow;
import io.genfin.refund.port.policy.ApprovalPolicy;
import io.genfin.refund.port.policy.ApprovalWorkflow;
import io.genfin.refund.port.policy.MaximumRefundPolicy;
import io.genfin.refund.port.policy.ReasonPolicy;
import io.genfin.refund.port.policy.RefundPolicy;
import io.genfin.refund.port.policy.RefundWindowPolicy;
import io.genfin.refund.port.reason.RefundReasonRegistry;
import java.time.Duration;
import java.util.NavigableMap;
import java.util.Optional;

/** Factory for the default policy implementations, mirroring {@code RefundCalculators}. */
public final class RefundPolicies {

  /**
   * No sensible universal default exists for how long a refund window should stay open; callers
   * pick a {@link Duration}. 180 days is a boring, widely-used starting point.
   */
  private static final Duration DEFAULT_WINDOW = Duration.ofDays(180);

  private RefundPolicies() {}

  public static RefundWindowPolicy defaultWindowPolicy() {
    return new DefaultRefundWindowPolicy(DEFAULT_WINDOW);
  }

  public static RefundWindowPolicy windowPolicy(Duration window) {
    return new DefaultRefundWindowPolicy(window);
  }

  /** No cap beyond the payment total itself — the refundable balance is the only limit. */
  public static MaximumRefundPolicy defaultMaximumRefundPolicy() {
    return new DefaultMaximumRefundPolicy();
  }

  public static MaximumRefundPolicy maximumRefundPolicy(Money cap) {
    return new DefaultMaximumRefundPolicy(Optional.of(cap));
  }

  /** Never requires approval unless a threshold is configured via {@link #approvalPolicy}. */
  public static ApprovalPolicy defaultApprovalPolicy() {
    return new DefaultApprovalPolicy();
  }

  public static ApprovalPolicy approvalPolicy(Money threshold) {
    return new DefaultApprovalPolicy(Optional.of(threshold));
  }

  /** No approval required at all: the workflow is satisfied automatically. */
  public static ApprovalWorkflow automaticApprovalWorkflow() {
    return new FixedApprovalWorkflow(0);
  }

  /** One explicit approval required. "Manual" and "Single" are the same one-approver model. */
  public static ApprovalWorkflow manualApprovalWorkflow() {
    return new FixedApprovalWorkflow(1);
  }

  public static ApprovalWorkflow singleApprovalWorkflow() {
    return new FixedApprovalWorkflow(1);
  }

  /** Maker-checker: two distinct approvals required. */
  public static ApprovalWorkflow twoPersonApprovalWorkflow() {
    return new FixedApprovalWorkflow(2);
  }

  /** Required approval count scales with amount; see {@link ThresholdApprovalWorkflow}. */
  public static ApprovalWorkflow thresholdApprovalWorkflow(NavigableMap<Money, Integer> tiers) {
    return new ThresholdApprovalWorkflow(tiers);
  }

  public static ReasonPolicy reasonPolicy(RefundReasonRegistry registry) {
    return new DefaultReasonPolicy(registry);
  }

  /** Composite policy wired from the four defaults above, scoped to {@code registry}'s reasons. */
  public static RefundPolicy standard(RefundReasonRegistry registry) {
    return new DefaultRefundPolicy(
        defaultWindowPolicy(),
        defaultMaximumRefundPolicy(),
        reasonPolicy(registry),
        defaultApprovalPolicy());
  }
}
