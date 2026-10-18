package io.genfin.refund.policy;

import static io.genfin.refund.support.TestCurrencies.USD;
import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.api.spi.ExtensionRegistries;
import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.money.money.Money;
import io.genfin.refund.calculation.RefundCalculatorFactory;
import io.genfin.refund.calculation.RefundCalculators;
import io.genfin.refund.port.calculation.RefundCalculator;
import io.genfin.refund.port.policy.ApprovalPolicy;
import io.genfin.refund.port.policy.RefundPolicy;
import io.genfin.refund.reason.ReasonFactory;
import io.genfin.refund.reason.RefundReasonBuilder;
import io.genfin.refund.reason.StandardRefundReason;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RefundPolicyBuilderAndFactoryTest {

  @Test
  void builderOverridesOnlyTheSuppliedSubPolicyAndDefaultsTheRest() {
    var registry = RefundReasonBuilder.newRegistry().standardCatalog().build();
    ApprovalPolicy alwaysRequiresApproval = context -> true;

    RefundPolicy policy =
        RefundPolicyBuilder.newPolicy(registry).approvalPolicy(alwaysRequiresApproval).build();

    var context =
        new RefundPolicyContext(
            Money.of("100.00", USD),
            List.of(),
            Money.of("10.00", USD),
            StandardRefundReason.CUSTOMER_REQUEST,
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-02T00:00:00Z"));
    assertThat(policy.evaluate(context).approvalRequired()).isTrue();
  }

  @Test
  void policyFactoryFallsBackToStandardWhenNoneRegistered() {
    ExtensionRegistry registry = ExtensionRegistries.create();

    RefundPolicy policy = RefundPolicyFactory.from(registry);

    assertThat(policy).isNotNull();
  }

  @Test
  void reasonFactoryFallsBackToTheStandardCatalog() {
    ExtensionRegistry registry = ExtensionRegistries.create();

    var reasonRegistry = ReasonFactory.from(registry);

    assertThat(reasonRegistry.find(StandardRefundReason.FRAUD)).isPresent();
  }

  @Test
  void calculatorFactoryFallsBackToStandard() {
    ExtensionRegistry registry = ExtensionRegistries.create();

    RefundCalculator calculator = RefundCalculatorFactory.from(registry);

    assertThat(calculator).isEqualTo(RefundCalculators.standard());
  }
}
