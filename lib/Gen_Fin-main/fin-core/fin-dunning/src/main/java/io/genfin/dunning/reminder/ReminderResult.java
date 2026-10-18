package io.genfin.dunning.reminder;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import java.util.Optional;

/**
 * The outcome of resolving one scheduled reminder occurrence: either a planned {@link Reminder}, or
 * a skip with a reason (no {@link ReminderRule} configured for that occurrence, already dispatched
 * per the {@link ReminderContext}, ...). Mirrors {@code RetryDecision}'s scheduled/exhausted shape
 * for the reminder side of planning.
 */
public record ReminderResult(int sequenceNumber, Reminder reminder, String skipReason)
    implements ValueObject {

  public ReminderResult {
    Validate.positive(sequenceNumber, "sequenceNumber must be positive.");
    Validate.required(
        (reminder == null) != (skipReason == null),
        "exactly one of reminder or skipReason must be set.");
  }

  public static ReminderResult planned(Reminder reminder) {
    Validate.notNull(reminder, "reminder must not be null.");
    return new ReminderResult(reminder.sequenceNumber(), reminder, null);
  }

  public static ReminderResult skipped(int sequenceNumber, String skipReason) {
    Validate.notBlank(skipReason, "skipReason must not be blank.");
    return new ReminderResult(sequenceNumber, null, skipReason);
  }

  public boolean isSkipped() {
    return reminder == null;
  }

  public Optional<Reminder> reminderIfPlanned() {
    return Optional.ofNullable(reminder);
  }
}
