package io.genfin.dunning.policy;

import io.genfin.api.validation.Validate;
import io.genfin.dunning.collection.CollectionPlan;
import io.genfin.dunning.id.CollectionPlanId;
import io.genfin.dunning.obligation.FinancialObligation;
import io.genfin.dunning.port.policy.DunningPolicy;
import java.time.Instant;

/**
 * Evaluates a resolved {@link DunningPolicy} against a {@link FinancialObligation}: turns the
 * policy's stage template into a concrete {@link CollectionPlan}, and answers whether the
 * obligation's grace period (carried on the policy's {@code SchedulePolicy}) has elapsed yet. Does
 * nothing beyond this composition - the actual reminder/retry/escalation timings still come from
 * the {@code SchedulePolicy}/{@code ReminderPolicy}/{@code EscalationPolicy} the policy composes.
 */
public final class PolicyEvaluator {

  public CollectionPlan collectionPlanFor(DunningPolicy policy) {
    Validate.notNull(policy, "policy must not be null.");
    return new CollectionPlan(CollectionPlanId.generate(), policy.id(), policy.stages());
  }

  /** True once {@code asOf} is past the policy's configured grace period for this obligation. */
  public boolean isDunningDue(DunningPolicy policy, FinancialObligation obligation, Instant asOf) {
    Validate.notNull(policy, "policy must not be null.");
    Validate.notNull(obligation, "obligation must not be null.");
    Validate.notNull(asOf, "asOf must not be null.");
    return policy.schedulePolicy().gracePeriod().hasExpired(obligation.dueDate(), asOf);
  }
}
