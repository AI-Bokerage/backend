package io.genfin.dunning.backoff;

import io.genfin.dunning.internal.backoff.ExponentialBackoff;
import io.genfin.dunning.internal.backoff.FibonacciBackoff;
import io.genfin.dunning.internal.backoff.FixedBackoff;
import io.genfin.dunning.internal.backoff.LinearBackoff;
import io.genfin.dunning.port.backoff.BackoffStrategy;

/**
 * Factory for the built-in {@link BackoffStrategy} shapes. Every strategy is parameterized by a
 * {@link BackoffInterval} an application supplies - none of these hardcode a base interval or time
 * unit. For any shape other than fixed/linear/exponential/fibonacci, an application implements
 * {@link BackoffStrategy} directly and uses it exactly like these.
 */
public final class BackoffStrategies {

  private BackoffStrategies() {}

  public static BackoffStrategy fixed(BackoffInterval interval) {
    return new FixedBackoff(interval);
  }

  public static BackoffStrategy linear(BackoffInterval interval) {
    return new LinearBackoff(interval);
  }

  public static BackoffStrategy exponential(BackoffInterval interval, double multiplier) {
    return new ExponentialBackoff(interval, multiplier);
  }

  public static BackoffStrategy fibonacci(BackoffInterval interval) {
    return new FibonacciBackoff(interval);
  }
}
