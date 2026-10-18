package io.genfin.dunning.reminder;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;

/**
 * Which {@link ReminderChannel} and {@link ReminderTemplateReference} an application wants for the
 * Nth reminder occurrence of an obligation's {@code DunningSchedule} (1-based, matching {@code
 * ScheduledEvent#sequenceNumber()} for {@code ScheduledEventType.REMINDER} entries). Carries no
 * timing of its own - the actual reminder date/time always comes from the resolved {@code
 * DunningSchedule}, never from this rule.
 */
public record ReminderRule(
    int sequenceNumber, ReminderChannel channel, ReminderTemplateReference template)
    implements ValueObject {

  public ReminderRule {
    Validate.positive(sequenceNumber, "sequenceNumber must be positive.");
    Validate.notNull(channel, "channel must not be null.");
    Validate.notNull(template, "template must not be null.");
  }

  public static ReminderRule of(
      int sequenceNumber, ReminderChannel channel, ReminderTemplateReference template) {
    return new ReminderRule(sequenceNumber, channel, template);
  }
}
