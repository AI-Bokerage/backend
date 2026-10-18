package io.genfin.dunning.calendar;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * A configurable span an obligation is allowed to sit overdue before dunning treats it as
 * delinquent - expressed as an amount plus an arbitrary {@link ChronoUnit} (minutes, hours, days,
 * weeks, ...), never a bare hardcoded day count.
 */
public record GracePeriod(long amount, ChronoUnit unit) implements ValueObject {

  public GracePeriod {
    Validate.nonNegative(amount, "amount must not be negative.");
    Validate.notNull(unit, "unit must not be null.");
  }

  public static GracePeriod of(long amount, ChronoUnit unit) {
    return new GracePeriod(amount, unit);
  }

  /** No grace at all - dunning may begin from the due date itself. */
  public static GracePeriod none() {
    return new GracePeriod(0, ChronoUnit.DAYS);
  }

  /** The instant this grace period elapses, measured from {@code dueDate}. */
  public Instant expiresAt(Instant dueDate) {
    Validate.notNull(dueDate, "dueDate must not be null.");
    return dueDate.plus(amount, unit);
  }

  public boolean hasExpired(Instant dueDate, Instant asOf) {
    Validate.notNull(asOf, "asOf must not be null.");
    return asOf.isAfter(expiresAt(dueDate));
  }
}
