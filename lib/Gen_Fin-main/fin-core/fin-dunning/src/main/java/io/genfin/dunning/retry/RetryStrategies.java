package io.genfin.dunning.retry;

import io.genfin.dunning.internal.retry.DefaultRetryStrategy;
import io.genfin.dunning.port.retry.RetryStrategy;

/**
 * Factory for {@link RetryStrategy} instances. For anything other than the standard
 * backoff-plus-calendar composition, an application implements {@link RetryStrategy} directly.
 */
public final class RetryStrategies {

  private static final RetryStrategy STANDARD = new DefaultRetryStrategy();

  private RetryStrategies() {}

  /** Composes a policy's {@code BackoffStrategy} with its {@code BusinessCalendar}. */
  public static RetryStrategy standard() {
    return STANDARD;
  }
}
