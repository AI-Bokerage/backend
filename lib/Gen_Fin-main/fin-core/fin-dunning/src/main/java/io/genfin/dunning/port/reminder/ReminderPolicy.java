package io.genfin.dunning.port.reminder;

import io.genfin.api.port.spi.Extension;
import io.genfin.dunning.reminder.ReminderSchedule;

/**
 * The full, explicit policy governing how the Reminder Engine resolves each scheduled reminder
 * occurrence: the {@link ReminderSchedule} of channel/template rules keyed by occurrence number. An
 * application resolves one of these from its own {@code DunningPolicy} - fin-dunning never
 * hardcodes a channel or template anywhere in default/example code paths.
 */
public interface ReminderPolicy extends Extension {

  ReminderSchedule reminderSchedule();
}
