package io.genfin.dunning.config;

import io.genfin.api.validation.Validate;
import io.genfin.dunning.port.reminder.ReminderPolicy;
import io.genfin.dunning.reminder.ReminderPolicies;

/**
 * Immutable, builder-based configuration for the Reminder concern: the resolved {@link
 * ReminderPolicy} (itself composed of an application's {@code ReminderRule}s). The empty-rules
 * default below exists only so {@code ReminderConfiguration.builder().build()} compiles and is
 * testable out of the box (every occurrence resolves to a skip) - resolving the real channel/
 * template rules for a deployment always flows through an application's own {@code DunningPolicy}.
 */
public final class ReminderConfiguration {

  private final ReminderPolicy reminderPolicy;

  private ReminderConfiguration(Builder builder) {
    this.reminderPolicy =
        Validate.notNull(builder.reminderPolicy, "reminderPolicy must not be null.");
  }

  public static Builder builder() {
    return new Builder();
  }

  public ReminderPolicy reminderPolicy() {
    return reminderPolicy;
  }

  public static final class Builder {

    private ReminderPolicy reminderPolicy = ReminderPolicies.standard();

    public Builder reminderPolicy(ReminderPolicy reminderPolicy) {
      this.reminderPolicy = reminderPolicy;
      return this;
    }

    public ReminderConfiguration build() {
      return new ReminderConfiguration(this);
    }
  }
}
