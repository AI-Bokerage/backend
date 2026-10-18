package io.genfin.dunning.config;

import io.genfin.api.validation.Validate;
import io.genfin.dunning.port.retry.RetryPolicy;
import io.genfin.dunning.retry.RetryPolicies;

/**
 * Immutable, builder-based configuration for the Retry concern: the resolved {@link RetryPolicy}
 * (itself composed of a {@code BackoffStrategy}, a {@code BusinessCalendar}, a zone, and a maximum
 * retry count). The all-defaulted example below exists only so {@code
 * RetryConfiguration.builder().build()} compiles and is testable out of the box - resolving the
 * real policy for a deployment always flows through an application's own {@code DunningPolicy}.
 */
public final class RetryConfiguration {

  private final RetryPolicy retryPolicy;

  private RetryConfiguration(Builder builder) {
    this.retryPolicy = Validate.notNull(builder.retryPolicy, "retryPolicy must not be null.");
  }

  public static Builder builder() {
    return new Builder();
  }

  public RetryPolicy retryPolicy() {
    return retryPolicy;
  }

  public static final class Builder {

    private RetryPolicy retryPolicy = RetryPolicies.standard();

    public Builder retryPolicy(RetryPolicy retryPolicy) {
      this.retryPolicy = retryPolicy;
      return this;
    }

    public RetryConfiguration build() {
      return new RetryConfiguration(this);
    }
  }
}
