package io.genfin.reconciliation.comparison;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;

/**
 * One discrepancy found between two {@link ComparisonRecord}s on a single dimension — e.g. the
 * amounts differ by 0.50, or the metadata key {@code "orderId"} carries different values.
 */
public record Difference(
    DifferenceType type,
    DifferenceSeverity severity,
    String field,
    String leftValue,
    String rightValue,
    String description)
    implements ValueObject {

  public Difference {
    Validate.notNull(type, "type must not be null.");
    Validate.notNull(severity, "severity must not be null.");
    Validate.notBlank(field, "field must not be blank.");
    Validate.notBlank(description, "description must not be blank.");
  }

  public static Difference of(
      DifferenceType type,
      DifferenceSeverity severity,
      String field,
      String leftValue,
      String rightValue,
      String description) {
    return new Difference(type, severity, field, leftValue, rightValue, description);
  }
}
