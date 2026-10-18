package io.genfin.dunning.internal.retry;

import io.genfin.api.validation.Validate;
import io.genfin.dunning.port.backoff.BackoffStrategy;
import io.genfin.dunning.port.calendar.BusinessCalendar;
import io.genfin.dunning.port.retry.RetryPolicy;
import io.genfin.dunning.retry.RetryPolicyConfiguration;
import java.time.ZoneId;

/** Adapts an immutable {@link RetryPolicyConfiguration} to the {@link RetryPolicy} port. */
public final class DefaultRetryPolicy implements RetryPolicy {

  private final RetryPolicyConfiguration configuration;

  public DefaultRetryPolicy(RetryPolicyConfiguration configuration) {
    this.configuration = Validate.notNull(configuration, "configuration must not be null.");
  }

  @Override
  public BackoffStrategy backoffStrategy() {
    return configuration.backoffStrategy();
  }

  @Override
  public BusinessCalendar businessCalendar() {
    return configuration.businessCalendar();
  }

  @Override
  public ZoneId zone() {
    return configuration.zone();
  }

  @Override
  public int maxRetries() {
    return configuration.maxRetries();
  }
}
