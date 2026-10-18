package io.genfin.dunning.retry;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.dunning.calendar.RetryWindow;
import java.time.Instant;
import java.util.Optional;

/**
 * The outcome of asking a {@code RetryStrategy} what should happen for one attempt number: either a
 * calendar-adjusted {@link RetryWindow} to retry within, or exhaustion (no further retry - the
 * obligation has used up every attempt its {@code RetryPolicy} allows). Data only - deciding never
 * executes anything.
 */
public record RetryDecision(int attemptNumber, RetryWindow window, boolean exhausted)
    implements ValueObject {

  public RetryDecision {
    Validate.positive(attemptNumber, "attemptNumber must be positive.");
    Validate.required(
        exhausted == (window == null), "window must be present exactly when not exhausted.");
  }

  public static RetryDecision scheduled(int attemptNumber, RetryWindow window) {
    return new RetryDecision(
        attemptNumber, Validate.notNull(window, "window must not be null."), false);
  }

  public static RetryDecision exhausted(int attemptNumber) {
    return new RetryDecision(attemptNumber, null, true);
  }

  /** The candidate retry instant, absent when {@link #exhausted()}. */
  public Optional<Instant> scheduledAt() {
    return window == null ? Optional.empty() : Optional.of(window.earliest());
  }
}
