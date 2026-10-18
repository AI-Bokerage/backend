package io.genfin.dunning.calendar;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.dunning.port.calendar.BusinessCalendar;
import java.time.Instant;
import java.time.ZoneId;

/**
 * The permitted window for one retry attempt, calendar-adjusted: an attempt number plus the {@link
 * TimeWindow} it may execute within. Carries no interval/backoff math itself - a {@code
 * BackoffStrategy} decides the candidate instant for an attempt, this type only anchors that
 * candidate onto a {@link BusinessCalendar}'s eligible days.
 */
public record RetryWindow(int attemptNumber, TimeWindow window) implements ValueObject {

  public RetryWindow {
    Validate.positive(attemptNumber, "attemptNumber must be positive.");
    Validate.notNull(window, "window must not be null.");
  }

  public static RetryWindow of(int attemptNumber, TimeWindow window) {
    return new RetryWindow(attemptNumber, window);
  }

  /**
   * Rolls {@code candidate} (interpreted in {@code zone}) onto an eligible day per {@code
   * calendar}, and returns the retry window spanning that whole day.
   */
  public static RetryWindow adjusted(
      int attemptNumber, Instant candidate, ZoneId zone, BusinessCalendar calendar) {
    Validate.notNull(candidate, "candidate must not be null.");
    Validate.notNull(zone, "zone must not be null.");
    Validate.notNull(calendar, "calendar must not be null.");
    java.time.LocalDate day = candidate.atZone(zone).toLocalDate();
    java.time.LocalDate rolledDay = calendar.roll(day);
    Instant start = rolledDay.atStartOfDay(zone).toInstant();
    Instant end = rolledDay.plusDays(1).atStartOfDay(zone).toInstant();
    return new RetryWindow(attemptNumber, TimeWindow.of(start, end));
  }

  public Instant earliest() {
    return window.start();
  }

  public Instant latest() {
    return window.end();
  }

  public boolean permits(Instant instant) {
    return window.contains(instant);
  }
}
