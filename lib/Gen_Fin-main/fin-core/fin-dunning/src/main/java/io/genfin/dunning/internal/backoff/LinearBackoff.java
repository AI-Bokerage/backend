package io.genfin.dunning.internal.backoff;

import io.genfin.api.validation.Validate;
import io.genfin.dunning.backoff.BackoffInterval;
import io.genfin.dunning.port.backoff.BackoffStrategy;
import java.time.Duration;

/** Delay grows linearly: attempt {@code n} waits {@code n} times the configured base interval. */
public final class LinearBackoff implements BackoffStrategy {

  private final BackoffInterval interval;

  public LinearBackoff(BackoffInterval interval) {
    this.interval = Validate.notNull(interval, "interval must not be null.");
  }

  @Override
  public Duration nextDelay(int attemptNumber) {
    Validate.positive(attemptNumber, "attemptNumber must be positive.");
    return interval.toDuration().multipliedBy(attemptNumber);
  }
}
