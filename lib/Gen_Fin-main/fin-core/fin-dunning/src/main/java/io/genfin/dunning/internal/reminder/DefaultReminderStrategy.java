package io.genfin.dunning.internal.reminder;

import io.genfin.api.validation.Validate;
import io.genfin.dunning.obligation.FinancialObligation;
import io.genfin.dunning.port.reminder.ReminderPolicy;
import io.genfin.dunning.port.reminder.ReminderStrategy;
import io.genfin.dunning.reminder.Reminder;
import io.genfin.dunning.reminder.ReminderContext;
import io.genfin.dunning.reminder.ReminderPlan;
import io.genfin.dunning.reminder.ReminderResult;
import io.genfin.dunning.reminder.ReminderRule;
import io.genfin.dunning.schedule.DunningSchedule;
import io.genfin.dunning.schedule.ScheduledEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Resolves each {@code REMINDER} entry of a {@link DunningSchedule} against the policy's {@code
 * ReminderSchedule} rules: a rule matching the entry's occurrence number produces a planned {@link
 * Reminder} using that rule's channel/template and the entry's own calendar-adjusted window; no
 * matching rule, or an occurrence already reported dispatched in the {@link ReminderContext},
 * produces a skip instead. Never sends anything.
 */
public final class DefaultReminderStrategy implements ReminderStrategy {

  @Override
  public ReminderPlan plan(
      FinancialObligation obligation,
      DunningSchedule schedule,
      ReminderPolicy policy,
      ReminderContext context) {
    Validate.notNull(obligation, "obligation must not be null.");
    Validate.notNull(schedule, "schedule must not be null.");
    Validate.notNull(policy, "policy must not be null.");
    Validate.notNull(context, "context must not be null.");

    List<ReminderResult> results = new ArrayList<>();
    for (ScheduledEvent event : schedule.reminderEvents()) {
      results.add(resolve(event, policy, context));
    }
    return ReminderPlan.of(results);
  }

  private static ReminderResult resolve(
      ScheduledEvent event, ReminderPolicy policy, ReminderContext context) {
    int sequenceNumber = event.sequenceNumber();
    if (context.alreadySent(sequenceNumber)) {
      return ReminderResult.skipped(sequenceNumber, "ALREADY_SENT");
    }

    Optional<ReminderRule> rule = policy.reminderSchedule().ruleForSequence(sequenceNumber);
    if (rule.isEmpty()) {
      return ReminderResult.skipped(sequenceNumber, "NO_RULE_CONFIGURED_FOR_SEQUENCE");
    }

    return ReminderResult.planned(
        new Reminder(sequenceNumber, rule.get().channel(), rule.get().template(), event.window()));
  }
}
