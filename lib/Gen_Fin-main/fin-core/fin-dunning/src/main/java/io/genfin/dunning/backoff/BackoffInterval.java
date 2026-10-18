package io.genfin.dunning.backoff;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * The configurable base interval a {@code BackoffStrategy} scales from - an amount plus an
 * arbitrary {@link ChronoUnit} (minutes, hours, days, weeks, ...). Never a bare hardcoded number: a
 * {@code DunningPolicy} always supplies this, built-in backoff strategies only know how to scale
 * it.
 */
public record BackoffInterval(long amount, ChronoUnit unit) implements ValueObject {

  public BackoffInterval {
    Validate.positive(amount, "amount must be positive.");
    Validate.notNull(unit, "unit must not be null.");
  }

  public static BackoffInterval of(long amount, ChronoUnit unit) {
    return new BackoffInterval(amount, unit);
  }

  public Duration toDuration() {
    // Duration.of(long, TemporalUnit) rejects WEEKS outright, so scale the unit's own exact
    // duration instead - this works uniformly for minutes/hours/days/weeks.
    return unit.getDuration().multipliedBy(amount);
  }
}
