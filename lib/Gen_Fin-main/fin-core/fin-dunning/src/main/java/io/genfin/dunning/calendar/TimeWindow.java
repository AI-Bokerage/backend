package io.genfin.dunning.calendar;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import java.time.Duration;
import java.time.Instant;

/**
 * A closed-open span of time [{@code start}, {@code end}). Generic on purpose: the same shape
 * describes a reminder send window, a permitted retry window, a quiet-hours exclusion window, or
 * any other bounded interval a policy needs - fin-dunning never assumes which.
 */
public record TimeWindow(Instant start, Instant end) implements ValueObject {

  public TimeWindow {
    Validate.notNull(start, "start must not be null.");
    Validate.notNull(end, "end must not be null.");
    Validate.required(!end.isBefore(start), "end must not be before start.");
  }

  public static TimeWindow of(Instant start, Instant end) {
    return new TimeWindow(start, end);
  }

  public Duration duration() {
    return Duration.between(start, end);
  }

  public boolean contains(Instant instant) {
    Validate.notNull(instant, "instant must not be null.");
    return !instant.isBefore(start) && instant.isBefore(end);
  }
}
