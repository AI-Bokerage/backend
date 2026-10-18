package io.genfin.dunning.validation;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.exception.Severity;
import io.genfin.api.validation.Validate;

/**
 * One finding raised by a {@link ValidationRule}. Mirrors {@code
 * io.genfin.ledger.validation.ValidationIssue} / {@code
 * io.genfin.pricing.validation.ValidationResult}'s issue shape - each engine keeps its own copy
 * rather than sharing one across modules.
 */
public record ValidationIssue(String ruleCode, String message, Severity severity)
    implements ValueObject {

  public ValidationIssue {
    Validate.notBlank(ruleCode, "ruleCode must not be blank.");
    Validate.notBlank(message, "message must not be blank.");
    Validate.notNull(severity, "severity must not be null.");
  }

  public static ValidationIssue of(String ruleCode, String message, Severity severity) {
    return new ValidationIssue(ruleCode, message, severity);
  }
}
