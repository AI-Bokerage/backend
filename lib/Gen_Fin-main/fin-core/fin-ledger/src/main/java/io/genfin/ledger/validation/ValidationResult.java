package io.genfin.ledger.validation;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.exception.Severity;
import io.genfin.api.util.CollectionUtils;
import java.util.List;

/**
 * The full set of issues a {@link io.genfin.ledger.port.validation.JournalValidator} run collected
 * - never short-circuits, so every {@link ValidationRule} contributes its findings to the same
 * result.
 */
public record ValidationResult(List<ValidationIssue> issues) implements ValueObject {

  public ValidationResult {
    issues = CollectionUtils.immutableList(issues);
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
