package io.genfin.providerapi.retry;

import io.genfin.api.domain.ValueObject;
import java.time.Duration;

public record RetryDecision(boolean shouldRetry, Duration delay, RetryClassification classification)
    implements ValueObject {

  public static RetryDecision retryAfter(Duration delay, RetryClassification classification) {
    return new RetryDecision(true, delay, classification);
  }

  public static RetryDecision doNotRetry(RetryClassification classification) {
    return new RetryDecision(false, Duration.ZERO, classification);
  }
}
