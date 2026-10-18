package io.genfin.dunning.notification;

import io.genfin.dunning.reminder.Reminder;
import io.genfin.dunning.reminder.ReminderPlan;
import java.util.List;

/**
 * Adapts other planning stages' output into {@link NotificationOccurrence}s. Only a reminder-plan
 * adapter exists today because reminder planning is the only upstream stage fin-dunning currently
 * produces; a future escalation stage would add its own adapter here alongside this one, not
 * replace it - which is exactly why notification planning is kept as its own layer rather than
 * folded into {@code io.genfin.dunning.reminder}.
 */
public final class NotificationOccurrences {

  private NotificationOccurrences() {}

  public static List<NotificationOccurrence> fromReminderPlan(ReminderPlan reminderPlan) {
    return reminderPlan.plannedReminders().stream()
        .map(NotificationOccurrences::fromReminder)
        .toList();
  }

  private static NotificationOccurrence fromReminder(Reminder reminder) {
    return NotificationOccurrence.of(
        reminder.sequenceNumber(),
        StandardNotificationSource.REMINDER,
        reminder.window(),
        reminder.channel(),
        reminder.template());
  }
}
