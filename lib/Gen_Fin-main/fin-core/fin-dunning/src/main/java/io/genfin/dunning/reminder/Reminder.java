package io.genfin.dunning.reminder;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.dunning.calendar.TimeWindow;

/**
 * One concrete, planned reminder: which occurrence it is, the {@link ReminderChannel} and {@link
 * ReminderTemplateReference} an application's {@link ReminderRule} resolved for it, and the
 * calendar-adjusted {@link TimeWindow} it falls in (taken directly from the obligation's {@code
 * DunningSchedule}). Data only - fin-dunning never sends this reminder on any channel.
 */
public record Reminder(
    int sequenceNumber,
    ReminderChannel channel,
    ReminderTemplateReference template,
    TimeWindow window)
    implements ValueObject {

  public Reminder {
    Validate.positive(sequenceNumber, "sequenceNumber must be positive.");
    Validate.notNull(channel, "channel must not be null.");
    Validate.notNull(template, "template must not be null.");
    Validate.notNull(window, "window must not be null.");
  }
}
