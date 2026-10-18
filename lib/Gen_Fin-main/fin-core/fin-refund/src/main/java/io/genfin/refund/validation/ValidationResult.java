package io.genfin.refund.validation;

import io.genfin.api.exception.Severity;
import java.util.List;

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
