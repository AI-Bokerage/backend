package io.genfin.reconciliation.validation;

import io.genfin.api.exception.Severity;
import java.util.List;

/** The full set of issues a {@link Validators} run collected. Never short-circuits. */
public record ValidationResult(List<ValidationIssue> issues) {

  public ValidationResult {
    issues = List.copyOf(issues);
  }

  public static ValidationResult valid() {
    return new ValidationResult(List.of());
  }

  public boolean isValid() {
    return issues.stream()
        .noneMatch(
            issue -> issue.severity() == Severity.ERROR || issue.severity() == Severity.CRITICAL);
  }
}
