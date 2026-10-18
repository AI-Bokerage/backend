package io.genfin.dunning.internal.backoff;

import io.genfin.api.validation.Validate;
import io.genfin.dunning.backoff.BackoffInterval;
import io.genfin.dunning.port.backoff.BackoffStrategy;
import java.time.Duration;

/**
 * Delay grows exponentially: attempt {@code n} waits {@code interval * multiplier^(n-1)}. The
 * multiplier is a configuration parameter, never a hardcoded constant - callers typically pass
 * {@code 2.0} for classic doubling, but any base is valid.
 */
public final class ExponentialBackoff implements BackoffStrategy {

  private final BackoffInterval interval;
  private final double multiplier;

  public ExponentialBackoff(BackoffInterval interval, double multiplier) {
    this.interval = Validate.notNull(interval, "interval must not be null.");
    Validate.argument(multiplier > 0, "multiplier must be positive.");
    this.multiplier = multiplier;
  }

  @Override
  public Duration nextDelay(int attemptNumber) {
    Validate.positive(attemptNumber, "attemptNumber must be positive.");
    double factor = Math.pow(multiplier, attemptNumber - 1);
    long nanos = Math.round(interval.toDuration().toNanos() * factor);
    return Duration.ofNanos(nanos);
  }
}
