package io.genfin.refund.spi;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.refund.calculation.RefundCalculators;
import io.genfin.refund.gateway.RefundGatewayExecutors;
import io.genfin.refund.lifecycle.RefundLifecycles;
import io.genfin.refund.policy.RefundPolicies;
import io.genfin.refund.port.calculation.RefundCalculator;
import io.genfin.refund.port.gateway.RefundGatewayExecutor;
import io.genfin.refund.port.lifecycle.RefundLifecycleProvider;
import io.genfin.refund.port.policy.ApprovalPolicy;
import io.genfin.refund.port.policy.ApprovalWorkflow;
import io.genfin.refund.port.policy.MaximumRefundPolicy;
import io.genfin.refund.port.policy.ReasonPolicy;
import io.genfin.refund.port.policy.RefundPolicy;
import io.genfin.refund.port.policy.RefundWindowPolicy;
import io.genfin.refund.port.reason.RefundReasonProvider;
import io.genfin.refund.port.reason.RefundReasonRegistry;
import io.genfin.refund.port.refund.RefundBuilderProvider;
import io.genfin.refund.port.validation.RefundRequestValidator;
import io.genfin.refund.port.validation.RefundValidator;
import io.genfin.refund.reason.RefundReasonRegistries;
import io.genfin.refund.refund.RefundBuilders;
import io.genfin.refund.validation.RefundRequestValidators;
import io.genfin.refund.validation.RefundValidators;

/**
 * Registers every default Refund-engine extension so downstream code discovers them through one
 * mechanism.
 */
public final class RefundExtensions {

  private RefundExtensions() {}

  public static void registerDefaults(ExtensionRegistry registry) {
    registry.register(RefundLifecycleProvider.class, RefundLifecycles.standard());
    registry.register(RefundCalculator.class, RefundCalculators.standard());
    registry.register(RefundReasonProvider.class, RefundReasonRegistries.standardCatalog());

    RefundReasonRegistry reasonRegistry =
        RefundReasonRegistries.withProvider(RefundReasonRegistries.standardCatalog());
    registry.register(RefundWindowPolicy.class, RefundPolicies.defaultWindowPolicy());
    registry.register(MaximumRefundPolicy.class, RefundPolicies.defaultMaximumRefundPolicy());
    registry.register(ReasonPolicy.class, RefundPolicies.reasonPolicy(reasonRegistry));
    registry.register(ApprovalPolicy.class, RefundPolicies.defaultApprovalPolicy());
    registry.register(ApprovalWorkflow.class, RefundPolicies.manualApprovalWorkflow());
    registry.register(RefundPolicy.class, RefundPolicies.standard(reasonRegistry));
    registry.register(
        RefundRequestValidator.class, RefundRequestValidators.standard(reasonRegistry));
    registry.register(RefundValidator.class, RefundValidators.standard(reasonRegistry));
    registry.register(RefundGatewayExecutor.class, RefundGatewayExecutors.standard());
    registry.register(RefundBuilderProvider.class, RefundBuilders.standard());
  }
}
