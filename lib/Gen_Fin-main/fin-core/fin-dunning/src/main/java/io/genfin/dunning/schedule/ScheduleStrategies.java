package io.genfin.dunning.schedule;

import io.genfin.dunning.internal.schedule.DefaultScheduleStrategy;
import io.genfin.dunning.port.schedule.ScheduleStrategy;

/**
 * Factory for {@link ScheduleStrategy} instances. For anything other than the standard
 * grace-plus-reminders-plus-retries composition, an application implements {@link ScheduleStrategy}
 * directly.
 */
public final class ScheduleStrategies {

  private static final ScheduleStrategy STANDARD = new DefaultScheduleStrategy();

  private ScheduleStrategies() {}

  /** Composes a policy's grace period, reminder offsets, and retry policy into one schedule. */
  public static ScheduleStrategy standard() {
    return STANDARD;
  }
}
