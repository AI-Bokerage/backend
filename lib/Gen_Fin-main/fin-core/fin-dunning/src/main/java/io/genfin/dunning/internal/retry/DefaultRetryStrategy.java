package io.genfin.dunning.internal.retry;

import io.genfin.api.validation.Validate;
import io.genfin.dunning.calendar.RetryWindow;
import io.genfin.dunning.port.retry.RetryPolicy;
import io.genfin.dunning.port.retry.RetryStrategy;
import io.genfin.dunning.retry.RetryDecision;
import java.time.Duration;
import java.time.Instant;

/**
 * Composes the policy's {@code BackoffStrategy} (spacing) with its {@code BusinessCalendar}
 * (eligible days): the backoff strategy proposes a candidate delay from {@code from}, and the
 * calendar rolls that candidate onto an eligible day per the policy's rules.
 */
public final class DefaultRetryStrategy implements RetryStrategy {

  @Override
  public RetryDecision decide(int attemptNumber, Instant from, RetryPolicy policy) {
    Validate.positive(attemptNumber, "attemptNumber must be positive.");
    Validate.notNull(from, "from must not be null.");
    Validate.notNull(policy, "policy must not be null.");

    if (attemptNumber > policy.maxRetries()) {
      return RetryDecision.exhausted(attemptNumber);
    }

    Duration delay = policy.backoffStrategy().nextDelay(attemptNumber);
    Instant candidate = from.plus(delay);
    RetryWindow window =
        RetryWindow.adjusted(attemptNumber, candidate, policy.zone(), policy.businessCalendar());
    return RetryDecision.scheduled(attemptNumber, window);
  }
}
