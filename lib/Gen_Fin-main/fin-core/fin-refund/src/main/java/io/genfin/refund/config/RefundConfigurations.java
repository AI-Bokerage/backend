package io.genfin.refund.config;

import io.genfin.refund.calculation.RefundCalculators;
import io.genfin.refund.lifecycle.RefundLifecycles;
import io.genfin.refund.policy.RefundPolicies;
import io.genfin.refund.port.reason.RefundReasonRegistry;
import io.genfin.refund.reason.RefundReasonRegistries;
import io.genfin.refund.validation.RefundRequestValidators;
import io.genfin.refund.validation.RefundValidators;

/** Factory for the default {@link RefundConfiguration}, mirroring {@code PaymentConfigurations}. */
public final class RefundConfigurations {

  private RefundConfigurations() {}

  public static RefundConfiguration standard() {
    RefundReasonRegistry reasonRegistry =
        RefundReasonRegistries.withProvider(RefundReasonRegistries.standardCatalog());
    return RefundConfiguration.builder()
        .lifecycleProvider(RefundLifecycles.standard())
        .approvalPolicy(RefundPolicies.defaultApprovalPolicy())
        .approvalWorkflow(RefundPolicies.manualApprovalWorkflow())
        .windowPolicy(RefundPolicies.defaultWindowPolicy())
        .maximumRefundPolicy(RefundPolicies.defaultMaximumRefundPolicy())
        .reasonPolicy(RefundPolicies.reasonPolicy(reasonRegistry))
        .calculator(RefundCalculators.standard())
        .validator(RefundValidators.standard(reasonRegistry))
        .requestValidator(RefundRequestValidators.standard(reasonRegistry))
        .build();
  }
}
