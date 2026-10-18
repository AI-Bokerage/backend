package io.genfin.dunning.notification;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.dunning.calendar.TimeWindow;
import io.genfin.dunning.reminder.ReminderChannel;
import io.genfin.dunning.reminder.ReminderTemplateReference;

/**
 * One concrete, planned notification: which {@link NotificationOccurrence} it resolves, the channel
 * and template a {@code NotificationStrategy} settled on (a matching {@link NotificationPreference}
 * if one applied, the occurrence's own fallback otherwise), and the window it falls in. Data only -
 * fin-dunning never sends this notification on any channel; the consuming application dispatches
 * it.
 */
public record NotificationStep(
    int sequenceNumber,
    NotificationSource source,
    ReminderChannel channel,
    ReminderTemplateReference template,
    TimeWindow window)
    implements ValueObject {

  public NotificationStep {
    Validate.positive(sequenceNumber, "sequenceNumber must be positive.");
    Validate.notNull(source, "source must not be null.");
    Validate.notNull(channel, "channel must not be null.");
    Validate.notNull(template, "template must not be null.");
    Validate.notNull(window, "window must not be null.");
  }
}
