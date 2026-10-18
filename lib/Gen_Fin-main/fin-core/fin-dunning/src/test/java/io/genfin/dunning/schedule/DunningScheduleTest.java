package io.genfin.dunning.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.dunning.backoff.BackoffInterval;
import io.genfin.dunning.backoff.BackoffStrategies;
import io.genfin.dunning.calendar.GracePeriod;
import io.genfin.dunning.obligation.FinancialObligation;
import io.genfin.dunning.port.retry.RetryPolicy;
import io.genfin.dunning.port.schedule.SchedulePolicy;
import io.genfin.dunning.reference.Reference;
import io.genfin.dunning.retry.RetryPolicies;
import io.genfin.dunning.retry.RetryPolicyConfiguration;
import io.genfin.dunning.support.TestCurrencies;
import io.genfin.dunning.support.TestObligationTypes;
import io.genfin.money.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;

class DunningScheduleTest {

  @Test
  void composesGracePeriodReminderOffsetsAndRetryPlanIntoOneOrderedSchedule() {
    Instant dueDate = LocalDate.of(2026, 7, 31).atStartOfDay(ZoneOffset.UTC).toInstant(); // Friday
    FinancialObligation obligation =
        FinancialObligation.of(
            Money.of(100, TestCurrencies.USD),
            dueDate,
            TestObligationTypes.INVOICE,
            Reference.obligationSource("obl-1"));

    RetryPolicy retryPolicy =
        RetryPolicies.of(
            RetryPolicyConfiguration.builder()
                .maxRetries(1)
                .backoffStrategy(BackoffStrategies.fixed(BackoffInterval.of(1, ChronoUnit.DAYS)))
                .zone(ZoneOffset.UTC)
                .build());
    SchedulePolicy policy =
        SchedulePolicies.of(
            SchedulePolicyConfiguration.builder()
                .gracePeriod(GracePeriod.of(2, ChronoUnit.DAYS))
                .retryPolicy(retryPolicy)
                .reminderOffsets(List.of(BackoffInterval.of(1, ChronoUnit.DAYS)))
                .build());

    DunningSchedule schedule = ScheduleStrategies.standard().schedule(obligation, policy);

    // Due Fri 07-31 + 1 day reminder offset = Sat 08-01 -> rolled forward to Mon 08-03.
    assertThat(schedule.reminderEvents()).hasSize(1);
    assertThat(schedule.reminderEvents().getFirst().window().start())
        .isEqualTo(LocalDate.of(2026, 8, 3).atStartOfDay(ZoneOffset.UTC).toInstant());

    // Grace expires Fri 07-31 + 2 days = Sun 08-02 -> rolled forward to Mon 08-03.
    assertThat(schedule.gracePeriodEnd()).isPresent();
    assertThat(schedule.gracePeriodEnd().orElseThrow().window().start())
        .isEqualTo(LocalDate.of(2026, 8, 3).atStartOfDay(ZoneOffset.UTC).toInstant());

    // Retries are planned from the (unrolled) grace expiry, so attempt 1 is +1 day from there.
    assertThat(schedule.retryEvents()).hasSize(1);
    assertThat(schedule.retryEvents().getFirst().sequenceNumber()).isEqualTo(1);

    assertThat(schedule.events())
        .isSortedAccordingTo((a, b) -> a.window().start().compareTo(b.window().start()));
  }

  @Test
  void aScheduleWithNoReminderOffsetsStillProducesGraceAndRetryEvents() {
    Instant dueDate = Instant.parse("2026-08-03T00:00:00Z"); // Monday
    FinancialObligation obligation =
        FinancialObligation.of(
            Money.of(50, TestCurrencies.USD),
            dueDate,
            TestObligationTypes.LOAN_INSTALLMENT,
            Reference.obligationSource("obl-2"));

    DunningSchedule schedule =
        ScheduleStrategies.standard().schedule(obligation, SchedulePolicies.standard());

    assertThat(schedule.reminderEvents()).isEmpty();
    assertThat(schedule.gracePeriodEnd()).isPresent();
    assertThat(schedule.retryEvents()).isNotEmpty();
  }
}
