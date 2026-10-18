package io.genfin.dunning.notification;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.dunning.calendar.TimeWindow;
import io.genfin.dunning.reminder.ReminderTemplateReference;
import io.genfin.dunning.reminder.StandardReminderChannel;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultNotificationStrategyTest {

  private static final TimeWindow WINDOW =
      TimeWindow.of(Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-02T00:00:00Z"));

  private static NotificationOccurrence occurrence(int sequenceNumber, NotificationSource source) {
    return NotificationOccurrence.of(
        sequenceNumber,
        source,
        WINDOW,
        StandardReminderChannel.EMAIL,
        ReminderTemplateReference.of("fallback-template"));
  }

  @Test
  void usesTheOccurrencesFallbackWhenNoPreferenceMatches() {
    NotificationPlan plan =
        NotificationStrategies.standard()
            .plan(
                List.of(occurrence(1, StandardNotificationSource.REMINDER)),
                NotificationSchedule.empty());

    assertThat(plan.steps()).hasSize(1);
    NotificationStep step = plan.steps().getFirst();
    assertThat(step.channel()).isEqualTo(StandardReminderChannel.EMAIL);
    assertThat(step.template().templateId()).isEqualTo("fallback-template");
  }

  @Test
  void aBlanketPreferenceOverridesEveryOccurrenceOfItsSource() {
    NotificationSchedule schedule =
        NotificationSchedule.of(
            List.of(
                NotificationPreference.forSource(
                    StandardNotificationSource.ESCALATION,
                    StandardReminderChannel.SMS,
                    ReminderTemplateReference.of("escalation-template"))));

    NotificationPlan plan =
        NotificationStrategies.standard()
            .plan(
                List.of(
                    occurrence(1, StandardNotificationSource.ESCALATION),
                    occurrence(2, StandardNotificationSource.ESCALATION)),
                schedule);

    assertThat(plan.steps()).allMatch(step -> step.channel().equals(StandardReminderChannel.SMS));
  }

  @Test
  void aSpecificOccurrencePreferenceWinsOverABlanketPreferenceForTheSameSource() {
    NotificationSchedule schedule =
        NotificationSchedule.of(
            List.of(
                NotificationPreference.forSource(
                    StandardNotificationSource.REMINDER,
                    StandardReminderChannel.SMS,
                    ReminderTemplateReference.of("blanket-template")),
                NotificationPreference.forOccurrence(
                    StandardNotificationSource.REMINDER,
                    1,
                    StandardReminderChannel.PUSH,
                    ReminderTemplateReference.of("specific-template"))));

    NotificationPlan plan =
        NotificationStrategies.standard()
            .plan(
                List.of(
                    occurrence(1, StandardNotificationSource.REMINDER),
                    occurrence(2, StandardNotificationSource.REMINDER)),
                schedule);

    NotificationStep first = plan.steps().get(0);
    NotificationStep second = plan.steps().get(1);
    assertThat(first.channel()).isEqualTo(StandardReminderChannel.PUSH);
    assertThat(first.template().templateId()).isEqualTo("specific-template");
    assertThat(second.channel()).isEqualTo(StandardReminderChannel.SMS);
    assertThat(second.template().templateId()).isEqualTo("blanket-template");
  }

  @Test
  void fromReminderPlanAdaptsOnlyPlannedRemindersIntoOccurrences() {
    io.genfin.dunning.reminder.ReminderResult planned =
        io.genfin.dunning.reminder.ReminderResult.planned(
            new io.genfin.dunning.reminder.Reminder(
                1, StandardReminderChannel.EMAIL, ReminderTemplateReference.of("t1"), WINDOW));
    io.genfin.dunning.reminder.ReminderResult skipped =
        io.genfin.dunning.reminder.ReminderResult.skipped(2, "NO_RULE_CONFIGURED_FOR_SEQUENCE");

    List<NotificationOccurrence> occurrences =
        NotificationOccurrences.fromReminderPlan(
            io.genfin.dunning.reminder.ReminderPlan.of(List.of(planned, skipped)));

    assertThat(occurrences).hasSize(1);
    assertThat(occurrences.getFirst().sequenceNumber()).isEqualTo(1);
    assertThat(occurrences.getFirst().source()).isEqualTo(StandardNotificationSource.REMINDER);
  }
}
