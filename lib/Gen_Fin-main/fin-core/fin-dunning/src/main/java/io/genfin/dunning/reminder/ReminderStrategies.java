package io.genfin.dunning.reminder;

import io.genfin.dunning.internal.reminder.DefaultReminderStrategy;
import io.genfin.dunning.port.reminder.ReminderStrategy;

/**
 * Factory for {@link ReminderStrategy} instances. For anything other than the standard
 * rule-lookup-per-occurrence composition, an application implements {@link ReminderStrategy}
 * directly.
 */
public final class ReminderStrategies {

  private static final ReminderStrategy STANDARD = new DefaultReminderStrategy();

  private ReminderStrategies() {}

  /** Resolves a policy's {@code ReminderSchedule} rules against a {@code DunningSchedule}. */
  public static ReminderStrategy standard() {
    return STANDARD;
  }
}
