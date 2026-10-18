package io.genfin.dunning.notification;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.dunning.calendar.TimeWindow;
import io.genfin.dunning.reminder.ReminderChannel;
import io.genfin.dunning.reminder.ReminderTemplateReference;

/**
 * One occurrence that may warrant a notification - a reminder that was actually planned, an
 * escalation action a future stage decided on, or anything else an application's own planning
 * produces - carrying whatever channel/template that upstream planning already resolved as a
 * fallback. A {@code NotificationStrategy} applies an application's {@link NotificationPreference}s
 * on top; where none override it, the fallback passes through unchanged. Reuses {@link
 * ReminderChannel}/{@link ReminderTemplateReference} rather than inventing notification-specific
 * twins of the same shape.
 */
public record NotificationOccurrence(
    int sequenceNumber,
    NotificationSource source,
    TimeWindow window,
    ReminderChannel fallbackChannel,
    ReminderTemplateReference fallbackTemplate)
    implements ValueObject {

  public NotificationOccurrence {
    Validate.positive(sequenceNumber, "sequenceNumber must be positive.");
    Validate.notNull(source, "source must not be null.");
    Validate.notNull(window, "window must not be null.");
    Validate.notNull(fallbackChannel, "fallbackChannel must not be null.");
    Validate.notNull(fallbackTemplate, "fallbackTemplate must not be null.");
  }

  public static NotificationOccurrence of(
      int sequenceNumber,
      NotificationSource source,
      TimeWindow window,
      ReminderChannel fallbackChannel,
      ReminderTemplateReference fallbackTemplate) {
    return new NotificationOccurrence(
        sequenceNumber, source, window, fallbackChannel, fallbackTemplate);
  }
}
