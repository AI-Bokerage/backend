package io.genfin.refund.spi;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.api.spi.ExtensionRegistries;
import io.genfin.api.spi.ExtensionRegistry;
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
import io.genfin.refund.port.refund.RefundBuilderProvider;
import io.genfin.refund.port.validation.RefundRequestValidator;
import io.genfin.refund.port.validation.RefundValidator;
import org.junit.jupiter.api.Test;

class RefundExtensionsTest {

  @Test
  void allDefaultRefundExtensionsAreDiscoverableAfterRegistration() {
    ExtensionRegistry registry = ExtensionRegistries.create();

    RefundExtensions.registerDefaults(registry);

    assertThat(registry.find(RefundLifecycleProvider.class)).isPresent();
    assertThat(registry.find(RefundCalculator.class)).isPresent();
    assertThat(registry.find(RefundReasonProvider.class)).isPresent();
    assertThat(registry.find(RefundWindowPolicy.class)).isPresent();
    assertThat(registry.find(MaximumRefundPolicy.class)).isPresent();
    assertThat(registry.find(ReasonPolicy.class)).isPresent();
    assertThat(registry.find(ApprovalPolicy.class)).isPresent();
    assertThat(registry.find(ApprovalWorkflow.class)).isPresent();
    assertThat(registry.find(RefundPolicy.class)).isPresent();
    assertThat(registry.find(RefundRequestValidator.class)).isPresent();
    assertThat(registry.find(RefundValidator.class)).isPresent();
    assertThat(registry.find(RefundGatewayExecutor.class)).isPresent();
    assertThat(registry.find(RefundBuilderProvider.class)).isPresent();
  }
}
