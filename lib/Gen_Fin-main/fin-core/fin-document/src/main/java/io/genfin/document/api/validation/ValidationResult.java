package io.genfin.document.api.validation;

import io.genfin.api.validation.Validate;
import java.util.List;

/** The outcome of validating a document/renderer/configuration combination before rendering. */
public final class ValidationResult {

  private final List<ValidationIssue> issues;

  private ValidationResult(List<ValidationIssue> issues) {
    Validate.notNull(issues, "issues must not be null");
    this.issues = List.copyOf(issues);
  }

  public static ValidationResult valid() {
    return new ValidationResult(List.of());
  }

  public static ValidationResult withIssues(List<ValidationIssue> issues) {
    return new ValidationResult(issues);
  }

  public boolean isValid() {
    return issues.isEmpty();
  }

  public List<ValidationIssue> issues() {
    return issues;
  }
}
