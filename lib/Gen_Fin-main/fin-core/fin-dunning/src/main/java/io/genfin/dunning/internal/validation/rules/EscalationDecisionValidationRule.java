package io.genfin.dunning.internal.validation.rules;

import io.genfin.api.exception.Severity;
import io.genfin.dunning.escalation.EscalationDecision;
import io.genfin.dunning.validation.ValidationContext;
import io.genfin.dunning.validation.ValidationIssue;
import io.genfin.dunning.validation.ValidationRule;
import java.util.List;

/**
 * Sanity check for an {@link EscalationDecision}: escalating on a zero attempt count would mean the
 * obligation escalated before a single attempt was ever made, which is never a valid ladder outcome
 * - an application's {@code EscalationRule}s always trigger after at least one attempt.
 */
public final class EscalationDecisionValidationRule implements ValidationRule {

  private static final String RULE_CODE = "ESCALATION_DECISION";

  @Override
  public List<ValidationIssue> apply(ValidationContext context) {
    EscalationDecision decision = context.escalationDecision();
    if (decision == null) {
      return List.of();
    }

    if (decision.escalated() && decision.attemptCount() == 0) {
      return List.of(
          ValidationIssue.of(
              RULE_CODE,
              "an escalated decision must have a positive attemptCount.",
              Severity.ERROR));
    }

    return List.of();
  }
}
