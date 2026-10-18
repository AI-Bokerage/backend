package io.genfin.dunning.schedule;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.dunning.calendar.TimeWindow;

/**
 * One planned, calendar-adjusted point in a {@link DunningSchedule}: a {@link ScheduledEventType},
 * a 1-based sequence number within that type (reminder 1, 2, ...; retry attempt 1, 2, ...; always 1
 * for the single grace-period-end entry), and the {@link TimeWindow} it is eligible to occur in.
 * Data only - fin-dunning never fires a reminder, retries a payment, or performs an escalation.
 */
public record ScheduledEvent(ScheduledEventType type, int sequenceNumber, TimeWindow window)
    implements ValueObject {

  public ScheduledEvent {
    Validate.notNull(type, "type must not be null.");
    Validate.positive(sequenceNumber, "sequenceNumber must be positive.");
    Validate.notNull(window, "window must not be null.");
  }
}
