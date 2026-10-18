package io.genfin.dunning.internal.backoff;

import io.genfin.api.validation.Validate;
import io.genfin.dunning.backoff.BackoffInterval;
import io.genfin.dunning.port.backoff.BackoffStrategy;
import java.time.Duration;

/**
 * Delay follows the Fibonacci sequence scaled by the configured base interval: attempt {@code n}
 * waits {@code interval * fib(n)}, with {@code fib(1) = fib(2) = 1}.
 */
public final class FibonacciBackoff implements BackoffStrategy {

  private final BackoffInterval interval;

  public FibonacciBackoff(BackoffInterval interval) {
    this.interval = Validate.notNull(interval, "interval must not be null.");
  }

  @Override
  public Duration nextDelay(int attemptNumber) {
    Validate.positive(attemptNumber, "attemptNumber must be positive.");
    return interval.toDuration().multipliedBy(fibonacci(attemptNumber));
  }

  private static long fibonacci(int n) {
    long previous = 0;
    long current = 1;
    for (int i = 1; i < n; i++) {
      long next = previous + current;
      previous = current;
      current = next;
    }
    return current;
  }
}
