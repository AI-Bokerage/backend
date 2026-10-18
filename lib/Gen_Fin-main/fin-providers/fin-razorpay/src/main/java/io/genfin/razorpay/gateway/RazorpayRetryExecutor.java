package io.genfin.razorpay.gateway;

import com.razorpay.RazorpayException;
import io.genfin.providerapi.port.retry.RetryPolicy;
import io.genfin.providerapi.retry.RetryClassification;
import io.genfin.providerapi.retry.RetryDecision;
import java.time.Duration;

/**
 * Wraps a single Razorpay SDK call site with the engine's {@link RetryPolicy} port, instead of the
 * Razorpay SDK being invoked exactly once with no retry behavior.
 */
final class RazorpayRetryExecutor {

  private static final int HTTP_TOO_MANY_REQUESTS = 429;
  private static final int NO_STATUS_CODE = 0;

  private final RetryPolicy retryPolicy;

  RazorpayRetryExecutor(RetryPolicy retryPolicy) {
    this.retryPolicy = retryPolicy;
  }

  @FunctionalInterface
  interface RazorpayCall<T> {
    T call() throws RazorpayException;
  }

  <T> T execute(RazorpayCall<T> call) throws RazorpayException {
    int attempt = 1;
    while (true) {
      try {
        return call.call();
      } catch (RazorpayException exception) {
        RetryDecision decision = retryPolicy.decide(attempt, classify(exception));
        if (!decision.shouldRetry()) {
          throw exception;
        }
        sleep(decision.delay());
        attempt++;
      }
    }
  }

  private static RetryClassification classify(RazorpayException exception) {
    if (exception.getStatusCode() == HTTP_TOO_MANY_REQUESTS) {
      return RetryClassification.RATE_LIMITED;
    }
    // The SDK reports statusCode 0 for a RazorpayException it wrapped around a raw IOException
    // (see ApiUtils#processRequest) — i.e. a connectivity failure rather than an API response.
    if (exception.getStatusCode() == NO_STATUS_CODE) {
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
          "Interrupted while waiting to retry a Razorpay call.", interrupted);
    }
  }
}
