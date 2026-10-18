package io.genfin.dunning.internal.escalation;

import io.genfin.api.validation.Validate;
import io.genfin.dunning.escalation.EscalationDecision;
import io.genfin.dunning.escalation.EscalationRule;
import io.genfin.dunning.port.escalation.EscalationPolicy;
import io.genfin.dunning.port.escalation.EscalationStrategy;
import java.util.Comparator;
import java.util.Optional;

/**
 * Picks the highest-threshold {@link EscalationRule} in the policy's ladder whose {@code
 * triggerAfterAttempts} is at or below the given attempt count - an obligation stays escalated at
 * that rung until a higher rule's threshold is also reached. No matching rule (an empty ladder, or
 * an attempt count below every rule's threshold) decides no escalation. Never performs any action.
 */
public final class DefaultEscalationStrategy implements EscalationStrategy {

  @Override
  public EscalationDecision decide(int attemptCount, EscalationPolicy policy) {
    Validate.nonNegative(attemptCount, "attemptCount must not be negative.");
    Validate.notNull(policy, "policy must not be null.");

    Optional<EscalationRule> matched =
        policy.escalationRules().stream()
            .filter(rule -> rule.triggerAfterAttempts() <= attemptCount)
            .max(Comparator.comparingInt(EscalationRule::triggerAfterAttempts));

    return matched
        .map(rule -> EscalationDecision.escalate(attemptCount, rule.level(), rule.action()))
        .orElseGet(() -> EscalationDecision.none(attemptCount));
  }
}
