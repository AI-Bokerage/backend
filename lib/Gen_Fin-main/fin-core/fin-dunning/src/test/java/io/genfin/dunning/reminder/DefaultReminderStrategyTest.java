package io.genfin.dunning.reminder;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.dunning.backoff.BackoffInterval;
import io.genfin.dunning.calendar.GracePeriod;
import io.genfin.dunning.obligation.FinancialObligation;
import io.genfin.dunning.port.reminder.ReminderPolicy;
import io.genfin.dunning.reference.Reference;
import io.genfin.dunning.schedule.DunningSchedule;
import io.genfin.dunning.schedule.SchedulePolicies;
import io.genfin.dunning.schedule.SchedulePolicyConfiguration;
import io.genfin.dunning.schedule.ScheduleStrategies;
import io.genfin.dunning.support.TestCurrencies;
import io.genfin.dunning.support.TestObligationTypes;
import io.genfin.money.money.Money;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DefaultReminderStrategyTest {

  private static final FinancialObligation OBLIGATION =
      FinancialObligation.of(
          Money.of(100, TestCurrencies.USD),
          Instant.parse("2026-07-01T00:00:00Z"),
          TestObligationTypes.INVOICE,
          Reference.obligationSource("obl-1"));

  private static DunningSchedule scheduleWithTwoReminders() {
    return ScheduleStrategies.standard()
        .schedule(
            OBLIGATION,
            SchedulePolicies.of(
                SchedulePolicyConfiguration.builder()
                    .gracePeriod(GracePeriod.of(1, ChronoUnit.DAYS))
                    .reminderOffsets(
                        List.of(
                            BackoffInterval.of(1, ChronoUnit.DAYS),
                            BackoffInterval.of(2, ChronoUnit.DAYS)))
                    .build()));
  }

  @Test
  void resolvesARuleMatchingReminderOccurrenceIntoAPlannedReminder() {
    ReminderPolicy policy =
        ReminderPolicies.of(
            ReminderSchedule.of(
                List.of(
                    ReminderRule.of(
                        1,
                        StandardReminderChannel.EMAIL,
                        ReminderTemplateReference.of("overdue-first-notice")))));

    ReminderPlan plan =
        ReminderStrategies.standard()
            .plan(
                OBLIGATION,
                scheduleWithTwoReminders(),
                policy,
                ReminderContext.asOf(Instant.parse("2026-07-01T00:00:00Z")));

    assertThat(plan.results()).hasSize(2);
    assertThat(plan.plannedReminders()).hasSize(1);
    Reminder planned = plan.plannedReminders().getFirst();
    assertThat(planned.sequenceNumber()).isEqualTo(1);
    assertThat(planned.channel()).isEqualTo(StandardReminderChannel.EMAIL);
    assertThat(planned.template().templateId()).isEqualTo("overdue-first-notice");
  }

  @Test
  void anOccurrenceWithNoRuleIsSkippedNotDefaulted() {
    ReminderPlan plan =
        ReminderStrategies.standard()
            .plan(
                OBLIGATION,
                scheduleWithTwoReminders(),
                ReminderPolicies.standard(),
                ReminderContext.asOf(Instant.parse("2026-07-01T00:00:00Z")));

    assertThat(plan.plannedReminders()).isEmpty();
    assertThat(plan.results()).allMatch(ReminderResult::isSkipped);
    assertThat(plan.results())
        .allMatch(result -> "NO_RULE_CONFIGURED_FOR_SEQUENCE".equals(result.skipReason()));
  }

  @Test
  void anAlreadySentOccurrenceIsSkippedEvenWithAMatchingRule() {
    ReminderPolicy policy =
        ReminderPolicies.of(
            ReminderSchedule.of(
                List.of(
                    ReminderRule.of(
                        1, StandardReminderChannel.SMS, ReminderTemplateReference.of("t1")))));

    ReminderPlan plan =
        ReminderStrategies.standard()
            .plan(
                OBLIGATION,
                scheduleWithTwoReminders(),
                policy,
                ReminderContext.of(Instant.parse("2026-07-01T00:00:00Z"), Set.of(1)));

    assertThat(plan.plannedReminders()).isEmpty();
    assertThat(plan.results().getFirst().skipReason()).isEqualTo("ALREADY_SENT");
  }
}
