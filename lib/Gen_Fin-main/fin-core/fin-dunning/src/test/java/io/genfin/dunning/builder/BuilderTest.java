package io.genfin.dunning.builder;

import static io.genfin.dunning.support.TestCurrencies.USD;
import static io.genfin.dunning.support.TestObligationTypes.INVOICE;
import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.dunning.collection.CollectionPlan;
import io.genfin.dunning.collection.CollectionPlanBuilder;
import io.genfin.dunning.collection.CollectionStage;
import io.genfin.dunning.dunning.DunningCase;
import io.genfin.dunning.dunning.DunningCaseBuilder;
import io.genfin.dunning.id.CollectionPlanId;
import io.genfin.dunning.id.DunningCaseId;
import io.genfin.dunning.id.DunningPolicyId;
import io.genfin.dunning.id.ObligationId;
import io.genfin.dunning.obligation.FinancialObligation;
import io.genfin.dunning.obligation.FinancialObligationBuilder;
import io.genfin.dunning.policy.PolicyBuilder;
import io.genfin.dunning.policy.PolicyVersion;
import io.genfin.dunning.port.policy.DunningPolicy;
import io.genfin.dunning.reference.Reference;
import io.genfin.money.money.Money;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Exercises the fluent builders directly (rather than only through their {@code Xxx.of(...)}/{@code
 * Xxx.standard()} static-factory callers elsewhere), confirming both their defaulting and their
 * field-by-field overrides.
 */
class BuilderTest {

  private static final Instant DUE_DATE = Instant.parse("2026-06-01T00:00:00Z");

  @Test
  void financialObligationBuilderGeneratesAnIdWhenNoneIsSupplied() {
    FinancialObligation obligation =
        FinancialObligationBuilder.newObligation()
            .amount(Money.of("100.00", USD))
            .dueDate(DUE_DATE)
            .type(INVOICE)
            .source(Reference.obligationSource("inv-1"))
            .build();

    assertThat(obligation.id()).isNotNull();
    assertThat(obligation.amount()).isEqualTo(Money.of("100.00", USD));
    assertThat(obligation.type()).isEqualTo(INVOICE);
  }

  @Test
  void financialObligationBuilderHonoursAnExplicitId() {
    ObligationId id = ObligationId.generate();

    FinancialObligation obligation =
        FinancialObligationBuilder.newObligation()
            .id(id)
            .amount(Money.of("50.00", USD))
            .dueDate(DUE_DATE)
            .type(INVOICE)
            .source(Reference.obligationSource("inv-2"))
            .build();

    assertThat(obligation.id()).isEqualTo(id);
  }

  @Test
  void collectionPlanBuilderAppendsStagesOneAtATimeOrInBulk() {
    CollectionPlan appended =
        CollectionPlanBuilder.newPlan()
            .policyId(DunningPolicyId.generate())
            .stage(CollectionStage.REMINDING)
            .stage(CollectionStage.RETRYING)
            .build();
    CollectionPlan bulk =
        CollectionPlanBuilder.newPlan()
            .policyId(DunningPolicyId.generate())
            .stages(List.of(CollectionStage.REMINDING, CollectionStage.RETRYING))
            .build();

    assertThat(appended.stages())
        .containsExactly(CollectionStage.REMINDING, CollectionStage.RETRYING);
    assertThat(bulk.stages()).containsExactlyElementsOf(appended.stages());
  }

  @Test
  void collectionPlanBuilderGeneratesAnIdWhenNoneIsSupplied() {
    CollectionPlan plan =
        CollectionPlanBuilder.newPlan()
            .policyId(DunningPolicyId.generate())
            .stage(CollectionStage.REMINDING)
            .build();

    assertThat(plan.id()).isNotNull().isInstanceOf(CollectionPlanId.class);
  }

  @Test
  void dunningCaseBuilderDefaultsIdAndClockButRequiresObligationAndPlan() {
    FinancialObligation obligation =
        FinancialObligation.of(
            Money.of("100.00", USD), DUE_DATE, INVOICE, Reference.obligationSource("inv-3"));
    CollectionPlan plan = CollectionPlan.standard(DunningPolicyId.generate());

    DunningCase dunningCase =
        DunningCaseBuilder.newCase().obligation(obligation).plan(plan).build();

    assertThat(dunningCase.id()).isNotNull();
    assertThat(dunningCase.stage()).isEqualTo(CollectionStage.OPEN);
  }

  @Test
  void dunningCaseBuilderHonoursAnExplicitId() {
    DunningCaseId id = DunningCaseId.generate();
    FinancialObligation obligation =
        FinancialObligation.of(
            Money.of("100.00", USD), DUE_DATE, INVOICE, Reference.obligationSource("inv-4"));
    CollectionPlan plan = CollectionPlan.standard(DunningPolicyId.generate());

    DunningCase dunningCase =
        DunningCaseBuilder.newCase().id(id).obligation(obligation).plan(plan).build();

    assertThat(dunningCase.id()).isEqualTo(id);
  }

  @Test
  void policyBuilderDefaultsEverySubPolicyButAcceptsOverrides() {
    DunningPolicy defaulted = PolicyBuilder.create().build();
    DunningPolicy customStages =
        PolicyBuilder.create()
            .id(DunningPolicyId.of("policy-custom"))
            .version(PolicyVersion.initial())
            .stages(List.of(CollectionStage.RETRYING))
            .build();

    assertThat(defaulted.id()).isNotNull();
    assertThat(customStages.id()).isEqualTo(DunningPolicyId.of("policy-custom"));
  }
}
