package io.genfin.dunning.config;

import io.genfin.api.validation.Validate;
import io.genfin.dunning.backoff.BackoffInterval;
import io.genfin.dunning.backoff.BackoffStrategies;
import io.genfin.dunning.port.backoff.BackoffStrategy;
import java.time.temporal.ChronoUnit;

/**
 * Immutable, builder-based configuration for the Backoff concern: the {@link BackoffStrategy} an
 * application resolves for a {@code RetryPolicy}. The fixed-one-day default below exists only so
 * {@code BackoffConfiguration.builder().build()} compiles and is testable out of the box -
 * resolving the real strategy/interval for a deployment always flows through an application's own
 * {@code DunningPolicy}, never this literal.
 */
public final class BackoffConfiguration {

  private final BackoffStrategy backoffStrategy;

  private BackoffConfiguration(Builder builder) {
    this.backoffStrategy =
        Validate.notNull(builder.backoffStrategy, "backoffStrategy must not be null.");
  }

  public static Builder builder() {
    return new Builder();
  }

  public BackoffStrategy backoffStrategy() {
    return backoffStrategy;
  }

  public static final class Builder {

    private BackoffStrategy backoffStrategy =
        BackoffStrategies.fixed(BackoffInterval.of(1, ChronoUnit.DAYS));

    public Builder backoffStrategy(BackoffStrategy backoffStrategy) {
      this.backoffStrategy = backoffStrategy;
      return this;
    }

    public BackoffConfiguration build() {
      return new BackoffConfiguration(this);
    }
  }
}
