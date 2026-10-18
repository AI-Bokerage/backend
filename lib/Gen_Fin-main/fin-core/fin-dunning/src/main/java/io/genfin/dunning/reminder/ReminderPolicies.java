package io.genfin.dunning.reminder;

import io.genfin.dunning.internal.reminder.DefaultReminderPolicy;
import io.genfin.dunning.port.reminder.ReminderPolicy;

/** Factory for {@link ReminderPolicy} instances. */
public final class ReminderPolicies {

  private ReminderPolicies() {}

  public static ReminderPolicy of(ReminderPolicyConfiguration configuration) {
    return new DefaultReminderPolicy(configuration.reminderSchedule());
  }

  public static ReminderPolicy of(ReminderSchedule reminderSchedule) {
    return new DefaultReminderPolicy(reminderSchedule);
  }

  /**
   * A policy built from a fully-defaulted example configuration - see {@link
   * ReminderPolicyConfiguration.Builder}. Carries no rules, so every reminder occurrence resolves
   * to a skip until an application supplies its own rules.
   */
  public static ReminderPolicy standard() {
    return of(ReminderPolicyConfiguration.builder().build());
  }
}
