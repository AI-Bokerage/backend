package io.genfin.providerapi.internal.retry;

import io.genfin.providerapi.port.retry.BackoffStrategy;
import io.genfin.providerapi.port.retry.RetryPolicy;
import io.genfin.providerapi.retry.RetryClassification;
import io.genfin.providerapi.retry.RetryDecision;

public final class DefaultRetryPolicy implements RetryPolicy {

  private final int maxAttempts;
  private final BackoffStrategy backoffStrategy;

  public DefaultRetryPolicy(int maxAttempts, BackoffStrategy backoffStrategy) {
    this.maxAttempts = maxAttempts;
    this.backoffStrategy = backoffStrategy;
  }

  @Override
  public RetryDecision decide(int attemptNumber, RetryClassification classification) {
    if (classification == RetryClassification.NON_RETRYABLE || attemptNumber >= maxAttempts) {
      return RetryDecision.doNotRetry(classification);
    }
    return RetryDecision.retryAfter(backoffStrategy.nextDelay(attemptNumber), classification);
  }
}
