package io.genfin.dunning.escalation;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;

/**
 * One rung of an application's escalation ladder: once an obligation's failed-attempt count reaches
 * {@code triggerAfterAttempts}, the resolved {@link EscalationLevel} and {@link EscalationAction}
 * apply. Carries no timing of its own - unlike {@code ReminderRule}'s exact occurrence match, a
 * rule stays in effect for every attempt count at or beyond its threshold until a higher rule's
 * threshold is reached, so an application can express a ladder with as many or as few rungs as it
 * needs. fin-dunning never decides what triggerAfterAttempts should be - that number, and the
 * level/action it maps to, always comes from an application's own {@code DunningPolicy}.
 */
public record EscalationRule(
    int triggerAfterAttempts, EscalationLevel level, EscalationAction action)
    implements ValueObject {

  public EscalationRule {
    Validate.positive(triggerAfterAttempts, "triggerAfterAttempts must be positive.");
    Validate.notNull(level, "level must not be null.");
    Validate.notNull(action, "action must not be null.");
  }

  public static EscalationRule of(
      int triggerAfterAttempts, EscalationLevel level, EscalationAction action) {
    return new EscalationRule(triggerAfterAttempts, level, action);
  }
}
