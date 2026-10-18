package io.genfin.dunning.validation;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.exception.Severity;
import io.genfin.api.util.CollectionUtils;
import java.util.List;

/**
 * The full set of issues a {@link io.genfin.dunning.port.validation.DunningValidator} run collected
 * - never short-circuits, so every {@link ValidationRule} contributes its findings to the same
 * result. Mirrors {@code io.genfin.ledger.validation.ValidationResult} / {@code
 * io.genfin.pricing.validation.ValidationResult}.
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
