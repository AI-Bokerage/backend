package io.genfin.dunning.schedule;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The full, coherent collection schedule computed for one {@code FinancialObligation}: when its
 * grace period ends, when each reminder falls, and when each retry attempt is eligible - every
 * entry already calendar-adjusted per the resolved {@code SchedulePolicy}. Produced by a {@code
 * ScheduleStrategy}; data only, fin-dunning never executes any of it.
 */
public record DunningSchedule(List<ScheduledEvent> events) implements ValueObject {

  public DunningSchedule {
    Validate.required(events != null && !events.isEmpty(), "events must not be empty.");
    events = events.stream().sorted(Comparator.comparing(e -> e.window().start())).toList();
  }

  public static DunningSchedule of(List<ScheduledEvent> events) {
    return new DunningSchedule(events);
  }

  public Optional<ScheduledEvent> gracePeriodEnd() {
    return events.stream().filter(e -> e.type() == ScheduledEventType.GRACE_PERIOD_END).findFirst();
  }

  public List<ScheduledEvent> reminderEvents() {
    return events.stream().filter(e -> e.type() == ScheduledEventType.REMINDER).toList();
  }

  public List<ScheduledEvent> retryEvents() {
    return events.stream().filter(e -> e.type() == ScheduledEventType.RETRY).toList();
  }

  /** The earliest scheduled event whose window starts strictly after {@code asOf}, if any. */
  public Optional<ScheduledEvent> nextEventAfter(Instant asOf) {
    Validate.notNull(asOf, "asOf must not be null.");
    return events.stream().filter(e -> e.window().start().isAfter(asOf)).findFirst();
  }
}
