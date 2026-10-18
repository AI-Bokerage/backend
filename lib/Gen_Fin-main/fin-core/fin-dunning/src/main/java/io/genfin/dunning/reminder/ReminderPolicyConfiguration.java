package io.genfin.dunning.reminder;

import java.util.List;

/**
 * Immutable, builder-based configuration for a {@code ReminderPolicy}. The empty default rule list
 * below exists only so {@code ReminderPolicyConfiguration.builder().build()} compiles and is
 * testable out of the box (an obligation with no rules simply gets every reminder occurrence
 * skipped) - resolving the real channel/template rules for a deployment always flows through an
 * application's own {@code DunningPolicy}, never a literal baked in here.
 */
public final class ReminderPolicyConfiguration {

  private final ReminderSchedule reminderSchedule;

  private ReminderPolicyConfiguration(Builder builder) {
    this.reminderSchedule = ReminderSchedule.of(builder.rules);
  }

  public static Builder builder() {
    return new Builder();
  }

  public ReminderSchedule reminderSchedule() {
    return reminderSchedule;
  }

  public static final class Builder {

    private List<ReminderRule> rules = List.of();

    public Builder rules(List<ReminderRule> rules) {
      this.rules = rules;
      return this;
    }

    public ReminderPolicyConfiguration build() {
      return new ReminderPolicyConfiguration(this);
    }
  }
}
