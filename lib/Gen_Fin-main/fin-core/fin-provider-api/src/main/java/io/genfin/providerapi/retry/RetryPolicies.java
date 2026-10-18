package io.genfin.providerapi.retry;

import io.genfin.providerapi.internal.retry.DefaultRetryPolicy;
import io.genfin.providerapi.internal.retry.ExponentialBackoffStrategy;
import io.genfin.providerapi.internal.retry.FixedBackoffStrategy;
import io.genfin.providerapi.port.retry.BackoffStrategy;
import io.genfin.providerapi.port.retry.RetryPolicy;
import java.time.Duration;

public final class RetryPolicies {

  private RetryPolicies() {}

  public static BackoffStrategy fixed(Duration delay) {
    return new FixedBackoffStrategy(delay);
  }

  public static BackoffStrategy exponential(Duration base, Duration max) {
    return new ExponentialBackoffStrategy(base, max);
  }

  public static RetryPolicy standard() {
    return new DefaultRetryPolicy(3, exponential(Duration.ofMillis(200), Duration.ofSeconds(10)));
  }
}
