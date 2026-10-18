package io.genfin.dunning.config;

import io.genfin.api.validation.Validate;
import io.genfin.dunning.port.schedule.SchedulePolicy;
import io.genfin.dunning.schedule.SchedulePolicies;

/**
 * Immutable, builder-based configuration for the Schedule concern: the resolved {@link
 * SchedulePolicy} (itself composed of a grace period, retry policy, and reminder offsets). The
 * all-defaulted example below exists only so {@code ScheduleConfiguration.builder().build()}
 * compiles and is testable out of the box - resolving the real policy for a deployment always flows
 * through an application's own {@code DunningPolicy}.
 */
public final class ScheduleConfiguration {

  private final SchedulePolicy schedulePolicy;

  private ScheduleConfiguration(Builder builder) {
    this.schedulePolicy =
        Validate.notNull(builder.schedulePolicy, "schedulePolicy must not be null.");
  }

  public static Builder builder() {
    return new Builder();
  }

  public SchedulePolicy schedulePolicy() {
    return schedulePolicy;
  }

  public static final class Builder {

    private SchedulePolicy schedulePolicy = SchedulePolicies.standard();

    public Builder schedulePolicy(SchedulePolicy schedulePolicy) {
      this.schedulePolicy = schedulePolicy;
      return this;
    }

    public ScheduleConfiguration build() {
      return new ScheduleConfiguration(this);
    }
  }
}
