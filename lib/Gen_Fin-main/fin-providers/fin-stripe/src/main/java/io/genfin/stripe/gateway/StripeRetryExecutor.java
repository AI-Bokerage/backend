package io.genfin.stripe.gateway;

import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.RateLimitException;
import com.stripe.exception.StripeException;
import io.genfin.providerapi.port.retry.RetryPolicy;
import io.genfin.providerapi.retry.RetryClassification;
import io.genfin.providerapi.retry.RetryDecision;
import java.time.Duration;

/**
 * Wraps a single Stripe SDK call site with the engine's {@link RetryPolicy} port, instead of the
 * Stripe SDK being invoked exactly once with no retry behavior.
 */
final class StripeRetryExecutor {

  private final RetryPolicy retryPolicy;

  StripeRetryExecutor(RetryPolicy retryPolicy) {
    this.retryPolicy = retryPolicy;
  }

  @FunctionalInterface
  interface StripeCall<T> {
    T call() throws StripeException;
  }

  <T> T execute(StripeCall<T> call) throws StripeException {
    int attempt = 1;
    while (true) {
      try {
        return call.call();
      } catch (StripeException exception) {
        RetryDecision decision = retryPolicy.decide(attempt, classify(exception));
        if (!decision.shouldRetry()) {
          throw exception;
        }
        sleep(decision.delay());
        attempt++;
      }
    }
  }

  private static RetryClassification classify(StripeException exception) {
    if (exception instanceof RateLimitException) {
      return RetryClassification.RATE_LIMITED;
    }
    if (exception instanceof ApiConnectionException) {
      return RetryClassification.RETRYABLE;
    }
    return RetryClassification.NON_RETRYABLE;
  }

  private static void sleep(Duration delay) {
    try {
      Thread.sleep(delay.toMillis());
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "Interrupted while waiting to retry a Stripe call.", interrupted);
    }
  }
}
