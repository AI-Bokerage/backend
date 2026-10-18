package io.genfin.dunning.schedule;

import io.genfin.dunning.internal.schedule.DefaultSchedulePolicy;
import io.genfin.dunning.port.schedule.SchedulePolicy;

/** Factory for {@link SchedulePolicy} instances. */
public final class SchedulePolicies {

  private SchedulePolicies() {}

  public static SchedulePolicy of(SchedulePolicyConfiguration configuration) {
    return new DefaultSchedulePolicy(configuration);
  }

  /**
   * A policy built from a fully-defaulted example configuration - see {@link
   * SchedulePolicyConfiguration.Builder}.
   */
  public static SchedulePolicy standard() {
    return of(SchedulePolicyConfiguration.builder().build());
  }
}
