package io.genfin.pricing.validation;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.exception.Severity;
import io.genfin.api.util.CollectionUtils;
import io.genfin.pricing.calculation.CalculationIssue;
import java.util.List;

/**
 * The full set of issues a {@link io.genfin.pricing.port.validation.PricingValidator} run collected
 * - never short-circuits, so every {@link ValidationRule} contributes its findings to the same
 * result. Mirrors {@code io.genfin.ledger.validation.ValidationResult}.
 */
public record ValidationResult(List<CalculationIssue> issues) implements ValueObject {

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
