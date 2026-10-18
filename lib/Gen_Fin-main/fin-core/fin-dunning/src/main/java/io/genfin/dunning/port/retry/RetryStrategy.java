package io.genfin.dunning.port.retry;

import io.genfin.api.port.spi.Extension;
import io.genfin.dunning.retry.RetryDecision;
import java.time.Instant;

/**
 * SPI deciding the actual next retry date/time for one attempt: composes a {@code BackoffStrategy}
 * (spacing) with a {@code BusinessCalendar} (eligible days), both read from the given {@link
 * RetryPolicy}. Plans only - never executes a retry.
 */
@FunctionalInterface
public interface RetryStrategy extends Extension {

  /**
   * Decides what should happen for one retry attempt.
   *
   * @param attemptNumber the 1-based retry attempt number being decided.
   * @param from the reference instant the backoff delay is measured from (e.g. the obligation's due
   *     date or first failure instant).
   * @param policy the resolved policy to decide against.
   */
  RetryDecision decide(int attemptNumber, Instant from, RetryPolicy policy);
}
