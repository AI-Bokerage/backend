package io.genfin.dunning.internal.backoff;

import io.genfin.api.validation.Validate;
import io.genfin.dunning.backoff.BackoffInterval;
import io.genfin.dunning.port.backoff.BackoffStrategy;
import java.time.Duration;

/** Same delay for every attempt, equal to the configured {@link BackoffInterval}. */
public final class FixedBackoff implements BackoffStrategy {

  private final BackoffInterval interval;

  public FixedBackoff(BackoffInterval interval) {
    this.interval = Validate.notNull(interval, "interval must not be null.");
  }

  @Override
  public Duration nextDelay(int attemptNumber) {
    Validate.positive(attemptNumber, "attemptNumber must be positive.");
    return interval.toDuration();
  }
}
