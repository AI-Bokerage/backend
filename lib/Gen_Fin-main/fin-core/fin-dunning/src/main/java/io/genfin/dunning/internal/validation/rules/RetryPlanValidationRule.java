package io.genfin.dunning.internal.validation.rules;

import io.genfin.api.exception.Severity;
import io.genfin.dunning.retry.RetryDecision;
import io.genfin.dunning.retry.RetryPlan;
import io.genfin.dunning.validation.ValidationContext;
import io.genfin.dunning.validation.ValidationIssue;
import io.genfin.dunning.validation.ValidationRule;
import java.util.ArrayList;
import java.util.List;

/**
 * Structural sanity checks for a {@link RetryPlan}: attempt numbers must be sequential starting
 * from 1, and an exhausted decision - if present - must only be the final one, never followed by a
 * scheduled attempt.
 */
public final class RetryPlanValidationRule implements ValidationRule {

  private static final String RULE_CODE = "RETRY_PLAN";

  @Override
  public List<ValidationIssue> apply(ValidationContext context) {
    RetryPlan plan = context.retryPlan();
    if (plan == null) {
      return List.of();
    }

    List<ValidationIssue> issues = new ArrayList<>();
    List<RetryDecision> decisions = plan.decisions();
    int expectedAttempt = 1;
    for (int index = 0; index < decisions.size(); index++) {
      RetryDecision decision = decisions.get(index);
      if (decision.attemptNumber() != expectedAttempt) {
        issues.add(
            ValidationIssue.of(
                RULE_CODE,
                "attemptNumber must be sequential starting from 1, expected "
                    + expectedAttempt
                    + " but found "
                    + decision.attemptNumber()
                    + ".",
                Severity.ERROR));
      }
      boolean isLast = index == decisions.size() - 1;
      if (decision.exhausted() && !isLast) {
        issues.add(
            ValidationIssue.of(
                RULE_CODE,
                "an exhausted decision must only appear as the final decision in the plan.",
                Severity.ERROR));
      }
      expectedAttempt++;
    }

    return issues;
  }
}
