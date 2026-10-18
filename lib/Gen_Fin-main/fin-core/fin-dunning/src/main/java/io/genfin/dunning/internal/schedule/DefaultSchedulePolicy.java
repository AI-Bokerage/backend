package io.genfin.dunning.internal.schedule;

import io.genfin.api.validation.Validate;
import io.genfin.dunning.backoff.BackoffInterval;
import io.genfin.dunning.calendar.GracePeriod;
import io.genfin.dunning.port.retry.RetryPolicy;
import io.genfin.dunning.port.schedule.SchedulePolicy;
import io.genfin.dunning.schedule.SchedulePolicyConfiguration;
import java.util.List;

/** Adapts an immutable {@link SchedulePolicyConfiguration} to the {@link SchedulePolicy} port. */
public final class DefaultSchedulePolicy implements SchedulePolicy {

  private final SchedulePolicyConfiguration configuration;

  public DefaultSchedulePolicy(SchedulePolicyConfiguration configuration) {
    this.configuration = Validate.notNull(configuration, "configuration must not be null.");
  }

  @Override
  public GracePeriod gracePeriod() {
    return configuration.gracePeriod();
  }

  @Override
  public RetryPolicy retryPolicy() {
    return configuration.retryPolicy();
  }

  @Override
  public List<BackoffInterval> reminderOffsets() {
    return configuration.reminderOffsets();
  }
}
