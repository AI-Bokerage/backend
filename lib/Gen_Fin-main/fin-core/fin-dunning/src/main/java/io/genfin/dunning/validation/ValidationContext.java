package io.genfin.dunning.validation;

import io.genfin.api.domain.ValueObject;
import io.genfin.dunning.collection.CollectionPlan;
import io.genfin.dunning.escalation.EscalationDecision;
import io.genfin.dunning.port.policy.DunningPolicy;
import io.genfin.dunning.reminder.ReminderPlan;
import io.genfin.dunning.retry.RetryPlan;

/**
 * Everything a {@link ValidationRule} may need for one validation pass. Every field is {@code null}
 * unless the caller enriches it via the {@code withXxx} methods - a rule that needs an object it
 * does not find simply reports no issue, so a single {@link Validators#standard()} pass can
 * validate whichever subset of {@link DunningPolicy}/{@link RetryPlan}/{@link ReminderPlan}/{@link
 * EscalationDecision}/{@link CollectionPlan} the caller happens to have on hand. Mirrors {@code
 * io.genfin.ledger.validation.ValidationContext} / {@code
 * io.genfin.pricing.validation.ValidationContext}.
 */
public record ValidationContext(
    DunningPolicy dunningPolicy,
    RetryPlan retryPlan,
    ReminderPlan reminderPlan,
    EscalationDecision escalationDecision,
    CollectionPlan collectionPlan)
    implements ValueObject {

  public static ValidationContext empty() {
    return new ValidationContext(null, null, null, null, null);
  }

  public static ValidationContext of(DunningPolicy dunningPolicy) {
    return empty().withDunningPolicy(dunningPolicy);
  }

  public ValidationContext withDunningPolicy(DunningPolicy dunningPolicy) {
    return new ValidationContext(
        dunningPolicy, retryPlan, reminderPlan, escalationDecision, collectionPlan);
  }

  public ValidationContext withRetryPlan(RetryPlan retryPlan) {
    return new ValidationContext(
        dunningPolicy, retryPlan, reminderPlan, escalationDecision, collectionPlan);
  }

  public ValidationContext withReminderPlan(ReminderPlan reminderPlan) {
    return new ValidationContext(
        dunningPolicy, retryPlan, reminderPlan, escalationDecision, collectionPlan);
  }

  public ValidationContext withEscalationDecision(EscalationDecision escalationDecision) {
    return new ValidationContext(
        dunningPolicy, retryPlan, reminderPlan, escalationDecision, collectionPlan);
  }

  public ValidationContext withCollectionPlan(CollectionPlan collectionPlan) {
    return new ValidationContext(
        dunningPolicy, retryPlan, reminderPlan, escalationDecision, collectionPlan);
  }
}
