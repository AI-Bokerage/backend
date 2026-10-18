package io.genfin.dunning.escalation;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import java.util.Optional;

/**
 * The outcome of asking an {@code EscalationStrategy} whether an obligation's current
 * failed-attempt count warrants escalation: either the highest matching {@link EscalationRule}'s
 * level/action, or no escalation at all. Data only - deciding never notifies a manager, suspends a
 * service, freezes an account, writes off the obligation, or performs any other action.
 */
public record EscalationDecision(
    int attemptCount, EscalationLevel level, EscalationAction action, boolean escalated)
    implements ValueObject {

  public EscalationDecision {
    Validate.nonNegative(attemptCount, "attemptCount must not be negative.");
    Validate.required(
        escalated == (level != null), "level must be present exactly when escalated.");
    Validate.required(
        escalated == (action != null), "action must be present exactly when escalated.");
  }

  public static EscalationDecision escalate(
      int attemptCount, EscalationLevel level, EscalationAction action) {
    return new EscalationDecision(
        attemptCount,
        Validate.notNull(level, "level must not be null."),
        Validate.notNull(action, "action must not be null."),
        true);
  }

  public static EscalationDecision none(int attemptCount) {
    return new EscalationDecision(attemptCount, null, null, false);
  }

  public Optional<EscalationLevel> levelIfEscalated() {
    return Optional.ofNullable(level);
  }

  public Optional<EscalationAction> actionIfEscalated() {
    return Optional.ofNullable(action);
  }
}
