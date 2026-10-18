package io.genfin.dunning.reminder;

import io.genfin.api.domain.ValueObject;
import java.util.List;
import java.util.Optional;

/**
 * The full, explicit set of {@link ReminderRule}s an application wants applied across an
 * obligation's reminder occurrences - which channel and template to use for reminder 1, 2, 3, ...
 * Resolved from an application's own {@code DunningPolicy}; fin-dunning ships none of its own.
 */
public record ReminderSchedule(List<ReminderRule> rules) implements ValueObject {

  public ReminderSchedule {
    rules = List.copyOf(rules);
  }

  public static ReminderSchedule of(List<ReminderRule> rules) {
    return new ReminderSchedule(rules);
  }

  public static ReminderSchedule empty() {
    return new ReminderSchedule(List.of());
  }

  public Optional<ReminderRule> ruleForSequence(int sequenceNumber) {
    return rules.stream().filter(rule -> rule.sequenceNumber() == sequenceNumber).findFirst();
  }
}
