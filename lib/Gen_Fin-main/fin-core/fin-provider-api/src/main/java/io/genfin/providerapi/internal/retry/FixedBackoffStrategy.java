package io.genfin.providerapi.internal.retry;

import io.genfin.providerapi.port.retry.BackoffStrategy;
import java.time.Duration;

public final class FixedBackoffStrategy implements BackoffStrategy {

  private final Duration delay;

  public FixedBackoffStrategy(Duration delay) {
    this.delay = delay;
  }

  @Override
  public Duration nextDelay(int attemptNumber) {
    return delay;
  }
}
