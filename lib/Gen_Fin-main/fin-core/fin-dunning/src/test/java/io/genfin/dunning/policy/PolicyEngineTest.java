package io.genfin.dunning.policy;

import static io.genfin.dunning.support.TestCurrencies.USD;
import static io.genfin.dunning.support.TestObligationTypes.INVOICE;
import static io.genfin.dunning.support.TestObligationTypes.LOAN_INSTALLMENT;
import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.dunning.calendar.GracePeriod;
import io.genfin.dunning.collection.CollectionStage;
import io.genfin.dunning.id.DunningPolicyId;
import io.genfin.dunning.obligation.FinancialObligation;
import io.genfin.dunning.obligation.ObligationType;
import io.genfin.dunning.port.policy.DunningPolicy;
import io.genfin.dunning.port.policy.PolicyRegistry;
import io.genfin.dunning.port.policy.PolicyResolver;
import io.genfin.dunning.reference.Reference;
import io.genfin.dunning.schedule.SchedulePolicies;
import io.genfin.dunning.schedule.SchedulePolicyConfiguration;
import io.genfin.money.money.Money;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PolicyEngineTest {

  private static final Instant DUE_DATE = Instant.parse("2026-06-01T00:00:00Z");

  @Test
  void resolverLooksUpTheRegisteredPolicyForTheObligationsType() {
    DunningPolicy invoicePolicy =
        DunningPolicies.builder().id(DunningPolicyId.of("policy-a")).build();
    DunningPolicy installmentPolicy =
        DunningPolicies.builder().id(DunningPolicyId.of("policy-b")).build();
    PolicyRegistry registry = PolicyRegistries.of(List.of(invoicePolicy, installmentPolicy));
    PolicyResolver resolver = PolicyResolvers.of(registry, type -> Optional.of(policyIdFor(type)));

    FinancialObligation invoice =
        FinancialObligation.of(
            Money.of("100.00", USD), DUE_DATE, INVOICE, Reference.obligationSource("inv-1"));

    assertThat(resolver.resolve(invoice)).contains(invoicePolicy);
  }

  @Test
  void resolverYieldsNoPolicyWhenNothingIsRegisteredForTheMapping() {
    PolicyRegistry registry = PolicyRegistries.empty();
    PolicyResolver resolver = PolicyResolvers.of(registry, type -> Optional.empty());
    FinancialObligation invoice =
        FinancialObligation.of(
            Money.of("100.00", USD), DUE_DATE, INVOICE, Reference.obligationSource("inv-1"));

    assertThat(resolver.resolve(invoice)).isEmpty();
  }

  @Test
  void evaluatorBuildsACollectionPlanFromThePolicysStageTemplate() {
    DunningPolicy policy =
        DunningPolicies.builder().stages(List.of(CollectionStage.RETRYING)).build();

    assertThat(new PolicyEvaluator().collectionPlanFor(policy).stages())
        .containsExactly(CollectionStage.RETRYING);
  }

  @Test
  void evaluatorHonoursThePolicysConfiguredGracePeriod() {
    DunningPolicy policy =
        DunningPolicies.builder()
            .schedulePolicy(
                SchedulePolicies.of(
                    SchedulePolicyConfiguration.builder()
                        .gracePeriod(GracePeriod.of(3, ChronoUnit.DAYS))
                        .build()))
            .build();
    FinancialObligation obligation =
        FinancialObligation.of(
            Money.of("100.00", USD), DUE_DATE, LOAN_INSTALLMENT, Reference.obligationSource("l-1"));
    PolicyEvaluator evaluator = new PolicyEvaluator();

    assertThat(evaluator.isDunningDue(policy, obligation, DUE_DATE.plus(2, ChronoUnit.DAYS)))
        .isFalse();
    assertThat(evaluator.isDunningDue(policy, obligation, DUE_DATE.plus(4, ChronoUnit.DAYS)))
        .isTrue();
  }

  private static DunningPolicyId policyIdFor(ObligationType type) {
    return type == INVOICE ? DunningPolicyId.of("policy-a") : DunningPolicyId.of("policy-b");
  }
}
