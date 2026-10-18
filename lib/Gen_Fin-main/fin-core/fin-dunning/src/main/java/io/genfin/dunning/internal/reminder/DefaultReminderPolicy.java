package io.genfin.dunning.internal.reminder;

import io.genfin.api.validation.Validate;
import io.genfin.dunning.port.reminder.ReminderPolicy;
import io.genfin.dunning.reminder.ReminderSchedule;

/** Adapts a {@link ReminderSchedule} to the {@link ReminderPolicy} port. */
public final class DefaultReminderPolicy implements ReminderPolicy {

  private final ReminderSchedule reminderSchedule;

  public DefaultReminderPolicy(ReminderSchedule reminderSchedule) {
    this.reminderSchedule =
        Validate.notNull(reminderSchedule, "reminderSchedule must not be null.");
  }

  @Override
  public ReminderSchedule reminderSchedule() {
    return reminderSchedule;
  }
}
