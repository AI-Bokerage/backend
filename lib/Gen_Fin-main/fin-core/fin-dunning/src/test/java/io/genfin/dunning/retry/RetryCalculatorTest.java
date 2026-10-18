package io.genfin.dunning.retry;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.dunning.backoff.BackoffInterval;
import io.genfin.dunning.backoff.BackoffStrategies;
import io.genfin.dunning.calendar.BusinessCalendars;
import io.genfin.dunning.calendar.CalendarPolicy;
import io.genfin.dunning.port.calendar.HolidayProvider;
import io.genfin.dunning.port.retry.RetryPolicy;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class RetryCalculatorTest {

  private static final HolidayProvider NO_HOLIDAYS = date -> false;

  @Test
  void planStopsAtMaxRetriesPlusOneExhaustedDecision() {
    RetryPolicy policy = RetryPolicies.of(RetryPolicyConfiguration.builder().maxRetries(2).build());
    RetryCalculator calculator = new RetryCalculator(RetryStrategies.standard());
    Instant from = LocalDate.of(2026, 8, 3).atStartOfDay(ZoneOffset.UTC).toInstant(); // Monday

    RetryPlan plan = calculator.plan(policy, from);

    assertThat(plan.decisions()).hasSize(3);
    assertThat(plan.scheduledAttemptCount()).isEqualTo(2);
    assertThat(plan.isExhausted()).isTrue();
    assertThat(plan.decisionForAttempt(3))
        .hasValueSatisfying(d -> assertThat(d.exhausted()).isTrue());
  }

  @Test
  void scheduledDecisionsAreRolledOntoBusinessDaysPerThePolicysCalendar() {
    RetryPolicy policy =
        RetryPolicies.of(
            RetryPolicyConfiguration.builder()
                .maxRetries(1)
                .backoffStrategy(BackoffStrategies.fixed(BackoffInterval.of(2, ChronoUnit.DAYS)))
                .businessCalendar(
                    BusinessCalendars.of(
                        CalendarPolicy.builder().holidayProvider(NO_HOLIDAYS).build()))
                .zone(ZoneOffset.UTC)
                .build());
    RetryCalculator calculator = new RetryCalculator(RetryStrategies.standard());
    // Friday 2026-07-31 + 2 days = Sunday 2026-08-02 -> rolled forward to Monday 2026-08-03.
    Instant from = LocalDate.of(2026, 7, 31).atStartOfDay(ZoneOffset.UTC).toInstant();

    RetryPlan plan = calculator.plan(policy, from);

    assertThat(plan.decisionForAttempt(1))
        .hasValueSatisfying(
            decision ->
                assertThat(decision.scheduledAt())
                    .hasValue(LocalDate.of(2026, 8, 3).atStartOfDay(ZoneOffset.UTC).toInstant()));
  }

  @Test
  void aMisbehavingCustomStrategyThatNeverReportsExhaustionIsStillBoundedByMaxRetries() {
    RetryPolicy policy = RetryPolicies.of(RetryPolicyConfiguration.builder().maxRetries(2).build());
    RetryCalculator calculator =
        new RetryCalculator(
            (attemptNumber, from, p) ->
                RetryDecision.scheduled(
                    attemptNumber,
                    io.genfin.dunning.calendar.RetryWindow.adjusted(
                        attemptNumber, from, p.zone(), p.businessCalendar())));

    RetryPlan plan = calculator.plan(policy, Instant.now());

    assertThat(plan.decisions()).hasSize(3);
  }
}
