package io.genfin.dunning.rule;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.dunning.calendar.GracePeriod;
import io.genfin.dunning.collection.CollectionAttributes;
import io.genfin.dunning.id.RetryId;
import io.genfin.dunning.obligation.FinancialObligation;
import io.genfin.dunning.port.retry.RetryPolicy;
import io.genfin.dunning.reference.Reference;
import io.genfin.dunning.retry.RetryExecution;
import io.genfin.dunning.retry.RetryHistory;
import io.genfin.dunning.retry.RetryPolicies;
import io.genfin.dunning.retry.RetryPolicyConfiguration;
import io.genfin.dunning.retry.RetryResult;
import io.genfin.dunning.support.TestCurrencies;
import io.genfin.dunning.support.TestObligationTypes;
import io.genfin.money.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;

class CollectionRuleEngineTest {

  private static final Instant MONDAY =
      LocalDate.of(2026, 8, 3).atStartOfDay(ZoneOffset.UTC).toInstant();
  private static final Instant SATURDAY =
      LocalDate.of(2026, 8, 1).atStartOfDay(ZoneOffset.UTC).toInstant();

  private FinancialObligation obligation(Money amount) {
    return FinancialObligation.of(
        amount, MONDAY, TestObligationTypes.INVOICE, Reference.obligationSource("OBL-1"));
  }

  @Test
  void engineCollectsEveryFiringRuleWithoutShortCircuiting() {
    Money amount = Money.of(BigDecimal.valueOf(100), TestCurrencies.USD);
    RetryPolicy retryPolicy =
        RetryPolicies.of(RetryPolicyConfiguration.builder().maxRetries(2).build());
    RetryHistory maxedOutHistory =
        RetryHistory.empty()
            .append(
                new RetryExecution(RetryId.generate(), 1, MONDAY, MONDAY, RetryResult.FAILED, null))
            .append(
                new RetryExecution(
                    RetryId.generate(), 2, MONDAY, MONDAY, RetryResult.FAILED, null));

    CollectionRuleContext context =
        CollectionRuleContext.of(obligation(amount), SATURDAY)
            .withAttributes(CollectionAttributes.standard().withSuspended(true))
            .withRetryHistory(maxedOutHistory)
            .withRetryPolicy(retryPolicy)
            .withGracePeriod(GracePeriod.of(1, ChronoUnit.DAYS));

    List<CollectionDecision> decisions = CollectionRuleEngines.standard().evaluate(context);

    // Grace period active, collection paused, holiday-skip-today, and max-retries-reached all
    // fire independently - the engine never stops after the first match.
    assertThat(decisions).hasSize(4);
    assertThat(decisions.stream().map(CollectionDecision::ruleCode))
        .containsExactlyInAnyOrder(
            "GRACE_PERIOD_ACTIVE",
            "COLLECTION_PAUSED",
            "HOLIDAY_SKIP_TODAY",
            "MAX_RETRIES_REACHED");
  }

  @Test
  void quietContextProducesNoDecisions() {
    Money amount = Money.of(BigDecimal.valueOf(100), TestCurrencies.USD);
    CollectionRuleContext context = CollectionRuleContext.of(obligation(amount), MONDAY);

    assertThat(CollectionRuleEngines.standard().evaluate(context)).isEmpty();
  }

  @Test
  void priorityRuleIsExplicitAndNotIncludedInDefaults() {
    Money highAmount = Money.of(BigDecimal.valueOf(10_000), TestCurrencies.USD);
    Money threshold = Money.of(BigDecimal.valueOf(5_000), TestCurrencies.USD);
    CollectionRuleContext context = CollectionRuleContext.of(obligation(highAmount), MONDAY);

    assertThat(
            CollectionRules.defaultRules().stream().map(Object::getClass).map(Class::getSimpleName))
        .doesNotContain("PriorityCollectionRule");

    List<CollectionDecision> decisions =
        CollectionRuleEngines.of(List.of(CollectionRules.priorityAbove(threshold)))
            .evaluate(context);

    assertThat(decisions)
        .singleElement()
        .satisfies(d -> assertThat(d.ruleCode()).isEqualTo("PRIORITY_AMOUNT"));
  }
}
