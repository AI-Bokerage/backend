package io.genfin.dunning.port.policy;

import io.genfin.api.port.spi.Extension;
import io.genfin.dunning.collection.CollectionStage;
import io.genfin.dunning.id.DunningPolicyId;
import io.genfin.dunning.policy.PolicyVersion;
import io.genfin.dunning.port.escalation.EscalationPolicy;
import io.genfin.dunning.port.reminder.ReminderPolicy;
import io.genfin.dunning.port.schedule.SchedulePolicy;
import java.util.List;

/**
 * The top-level, explicit policy an application registers for one kind of receivable: an invoice's
 * overdue policy, a subscription's renewal-retry policy, a loan installment's collection policy,
 * and so on. Composes every other resolvable policy from Stages 2-4 - the {@link SchedulePolicy}
 * (itself carrying the grace period and {@code RetryPolicy}, which in turn carries the {@code
 * BackoffStrategy} and {@code BusinessCalendar}), the {@link ReminderPolicy}, and the {@link
 * EscalationPolicy} - plus the ordered {@link CollectionStage} template a {@code DunningCase}
 * following this policy is expected to progress through.
 *
 * <p>fin-dunning ships no built-in policy of its own beyond an all-defaults example (see {@code
 * DunningPolicies#standard()}); every real policy - "1/3/5 days then suspend", "6/12/24 hours then
 * escalate/write-off", "every weekday, skip weekends, max 8 retries, never suspend" - is data an
 * application builds via {@link io.genfin.dunning.policy.PolicyBuilder} and registers through a
 * {@link PolicyRegistry}.
 */
public interface DunningPolicy extends Extension {

  DunningPolicyId id();

  PolicyVersion version();

  SchedulePolicy schedulePolicy();

  ReminderPolicy reminderPolicy();

  EscalationPolicy escalationPolicy();

  /**
   * The ordered stage template this policy's cases progress through - an application may configure
   * any subset/order of {@link CollectionStage}s (e.g. omitting {@code ESCALATING} entirely for a
   * policy that never escalates).
   */
  List<CollectionStage> stages();
}
