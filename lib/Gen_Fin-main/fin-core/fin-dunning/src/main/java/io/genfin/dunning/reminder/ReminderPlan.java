package io.genfin.dunning.reminder;

import io.genfin.api.domain.ValueObject;
import java.util.List;

/**
 * The full, ordered set of {@link ReminderResult}s computed for an obligation's reminder
 * occurrences - one per {@code DunningSchedule} reminder event. Produced by a {@code
 * ReminderStrategy}; never executed by fin-dunning - the consuming application dispatches each
 * planned {@link Reminder} itself.
 */
public record ReminderPlan(List<ReminderResult> results) implements ValueObject {

  public ReminderPlan {
    results = List.copyOf(results);
  }

  public static ReminderPlan of(List<ReminderResult> results) {
    return new ReminderPlan(results);
  }

  /** The reminders actually planned to go out, skipping any suppressed occurrences. */
  public List<Reminder> plannedReminders() {
    return results.stream().flatMap(result -> result.reminderIfPlanned().stream()).toList();
  }
}
