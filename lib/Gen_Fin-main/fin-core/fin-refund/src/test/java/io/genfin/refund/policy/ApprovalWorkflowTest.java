package io.genfin.refund.policy;

import static io.genfin.refund.support.TestCurrencies.USD;
import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.money.money.Money;
import io.genfin.refund.approval.ApprovalRequirement;
import io.genfin.refund.port.policy.ApprovalWorkflow;
import io.genfin.refund.reason.StandardRefundReason;
import java.time.Instant;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class ApprovalWorkflowTest {

  private static final Instant PAID_AT = Instant.parse("2026-01-01T00:00:00Z");

  private static RefundPolicyContext contextFor(Money requestedAmount) {
    return new RefundPolicyContext(
        Money.of("1000.00", USD),
        List.of(),
        requestedAmount,
        StandardRefundReason.CUSTOMER_REQUEST,
        PAID_AT,
        PAID_AT);
  }

  @Test
  void automaticWorkflowNeverRequiresApproval() {
    ApprovalWorkflow workflow = RefundPolicies.automaticApprovalWorkflow();

    ApprovalRequirement requirement = workflow.requirementFor(contextFor(Money.of("500.00", USD)));

    assertThat(requirement.isAutomatic()).isTrue();
    assertThat(requirement.isSatisfiedBy(0)).isTrue();
  }

  @Test
  void manualAndSingleWorkflowsRequireExactlyOneApproval() {
    ApprovalRequirement manual =
        RefundPolicies.manualApprovalWorkflow().requirementFor(contextFor(Money.of("10.00", USD)));
    ApprovalRequirement single =
        RefundPolicies.singleApprovalWorkflow().requirementFor(contextFor(Money.of("10.00", USD)));

    assertThat(manual.requiredApprovals()).isEqualTo(1);
    assertThat(single.requiredApprovals()).isEqualTo(1);
    assertThat(manual.isSatisfiedBy(1)).isTrue();
    assertThat(manual.isSatisfiedBy(0)).isFalse();
  }

  @Test
  void twoPersonWorkflowRequiresTwoDistinctApprovals() {
    ApprovalRequirement requirement =
        RefundPolicies.twoPersonApprovalWorkflow()
            .requirementFor(contextFor(Money.of("10.00", USD)));

    assertThat(requirement.isSatisfiedBy(1)).isFalse();
    assertThat(requirement.isSatisfiedBy(2)).isTrue();
  }

  @Test
  void thresholdWorkflowScalesRequiredApprovalsWithAmount() {
    NavigableMap<Money, Integer> tiers = new TreeMap<>();
    tiers.put(Money.of("0.00", USD), 0);
    tiers.put(Money.of("100.00", USD), 1);
    tiers.put(Money.of("1000.00", USD), 2);
    ApprovalWorkflow workflow = RefundPolicies.thresholdApprovalWorkflow(tiers);

    assertThat(workflow.requirementFor(contextFor(Money.of("50.00", USD))).requiredApprovals())
        .isEqualTo(0);
    assertThat(workflow.requirementFor(contextFor(Money.of("500.00", USD))).requiredApprovals())
        .isEqualTo(1);
    assertThat(workflow.requirementFor(contextFor(Money.of("5000.00", USD))).requiredApprovals())
        .isEqualTo(2);
  }
}
