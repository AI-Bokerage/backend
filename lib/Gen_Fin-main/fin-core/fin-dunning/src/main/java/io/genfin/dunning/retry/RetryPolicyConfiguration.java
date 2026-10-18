package io.genfin.dunning.retry;

import io.genfin.api.validation.Validate;
import io.genfin.dunning.backoff.BackoffInterval;
import io.genfin.dunning.backoff.BackoffStrategies;
import io.genfin.dunning.calendar.BusinessCalendars;
import io.genfin.dunning.port.backoff.BackoffStrategy;
import io.genfin.dunning.port.calendar.BusinessCalendar;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * Immutable, builder-based configuration for a {@code RetryPolicy}. Every builder default below
 * exists only so {@code RetryPolicyConfiguration.builder().build()} compiles and is testable out of
 * the box - resolving the real policy for a deployment always flows through an application's own
 * {@code DunningPolicy}, never these literals.
 */
public final class RetryPolicyConfiguration {

  private final BackoffStrategy backoffStrategy;
  private final BusinessCalendar businessCalendar;
  private final ZoneId zone;
  private final int maxRetries;

  private RetryPolicyConfiguration(Builder builder) {
    this.backoffStrategy =
        Validate.notNull(builder.backoffStrategy, "backoffStrategy must not be null.");
    this.businessCalendar =
        Validate.notNull(builder.businessCalendar, "businessCalendar must not be null.");
    this.zone = Validate.notNull(builder.zone, "zone must not be null.");
    this.maxRetries = Validate.positive(builder.maxRetries, "maxRetries must be positive.");
  }

  public static Builder builder() {
    return new Builder();
  }

  public BackoffStrategy backoffStrategy() {
    return backoffStrategy;
  }

  public BusinessCalendar businessCalendar() {
    return businessCalendar;
  }

  public ZoneId zone() {
    return zone;
  }

  public int maxRetries() {
    return maxRetries;
  }

  public static final class Builder {

    private BackoffStrategy backoffStrategy =
        BackoffStrategies.fixed(BackoffInterval.of(1, ChronoUnit.DAYS));
    private BusinessCalendar businessCalendar = BusinessCalendars.standard();
    private ZoneId zone = ZoneOffset.UTC;
    private int maxRetries = 3;

    public Builder backoffStrategy(BackoffStrategy backoffStrategy) {
      this.backoffStrategy = backoffStrategy;
      return this;
    }

    public Builder businessCalendar(BusinessCalendar businessCalendar) {
      this.businessCalendar = businessCalendar;
      return this;
    }

    public Builder zone(ZoneId zone) {
      this.zone = zone;
      return this;
    }

    public Builder maxRetries(int maxRetries) {
      this.maxRetries = maxRetries;
      return this;
    }

    public RetryPolicyConfiguration build() {
      return new RetryPolicyConfiguration(this);
    }
  }
}
