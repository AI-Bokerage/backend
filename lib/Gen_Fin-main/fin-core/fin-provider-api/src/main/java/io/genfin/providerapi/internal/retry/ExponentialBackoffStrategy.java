package io.genfin.providerapi.internal.retry;

import io.genfin.providerapi.port.retry.BackoffStrategy;
import java.time.Duration;

public final class ExponentialBackoffStrategy implements BackoffStrategy {

  private final Duration base;
  private final Duration max;

  public ExponentialBackoffStrategy(Duration base, Duration max) {
    this.base = base;
    this.max = max;
  }

  @Override
  public Duration nextDelay(int attemptNumber) {
    Duration delay = base.multipliedBy(1L << Math.max(0, attemptNumber - 1));
    return delay.compareTo(max) > 0 ? max : delay;
  }
}
