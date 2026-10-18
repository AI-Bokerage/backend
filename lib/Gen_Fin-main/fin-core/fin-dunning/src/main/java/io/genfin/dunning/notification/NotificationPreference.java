package io.genfin.dunning.notification;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.dunning.reminder.ReminderChannel;
import io.genfin.dunning.reminder.ReminderTemplateReference;

/**
 * An application's declared override of the channel/template a {@link NotificationOccurrence}'s
 * fallback would otherwise resolve to. {@code sequenceNumber} is optional: {@code null} matches
 * every occurrence of {@code source} (a blanket preference, e.g. "all ESCALATION notifications go
 * by SMS"), set matches only that specific occurrence number of that source (e.g. "reminder #1
 * specifically goes by PUSH instead of its usual channel").
 */
public record NotificationPreference(
    NotificationSource source,
    Integer sequenceNumber,
    ReminderChannel channel,
    ReminderTemplateReference template)
    implements ValueObject {

  public NotificationPreference {
    Validate.notNull(source, "source must not be null.");
    Validate.notNull(channel, "channel must not be null.");
    Validate.notNull(template, "template must not be null.");
    if (sequenceNumber != null) {
      Validate.positive(sequenceNumber, "sequenceNumber must be positive when specified.");
    }
  }

  /** A blanket preference applying to every occurrence of {@code source}. */
  public static NotificationPreference forSource(
      NotificationSource source, ReminderChannel channel, ReminderTemplateReference template) {
    return new NotificationPreference(source, null, channel, template);
  }

  /** A preference applying only to one specific occurrence number of {@code source}. */
  public static NotificationPreference forOccurrence(
      NotificationSource source,
      int sequenceNumber,
      ReminderChannel channel,
      ReminderTemplateReference template) {
    return new NotificationPreference(source, sequenceNumber, channel, template);
  }

  boolean matches(NotificationSource candidateSource, int candidateSequenceNumber) {
    return source.code().equals(candidateSource.code())
        && (sequenceNumber == null || sequenceNumber == candidateSequenceNumber);
  }

  boolean isSpecific() {
    return sequenceNumber != null;
  }
}
