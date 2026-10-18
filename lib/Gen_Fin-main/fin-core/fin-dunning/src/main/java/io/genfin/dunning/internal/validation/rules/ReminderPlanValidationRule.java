package io.genfin.dunning.internal.validation.rules;

import io.genfin.api.exception.Severity;
import io.genfin.dunning.reminder.ReminderPlan;
import io.genfin.dunning.reminder.ReminderResult;
import io.genfin.dunning.validation.ValidationContext;
import io.genfin.dunning.validation.ValidationIssue;
import io.genfin.dunning.validation.ValidationRule;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Structural sanity checks for a {@link ReminderPlan}: every occurrence's sequence number must be
 * unique - a duplicate would mean two results were produced for the same scheduled reminder.
 */
public final class ReminderPlanValidationRule implements ValidationRule {

  private static final String RULE_CODE = "REMINDER_PLAN";

  @Override
  public List<ValidationIssue> apply(ValidationContext context) {
    ReminderPlan plan = context.reminderPlan();
    if (plan == null) {
      return List.of();
    }

    List<ValidationIssue> issues = new ArrayList<>();
    Set<Integer> seen = new HashSet<>();
    for (ReminderResult result : plan.results()) {
      if (!seen.add(result.sequenceNumber())) {
        issues.add(
            ValidationIssue.of(
                RULE_CODE,
                "duplicate sequenceNumber " + result.sequenceNumber() + " in reminder plan.",
                Severity.ERROR));
      }
    }

    return issues;
  }
}
