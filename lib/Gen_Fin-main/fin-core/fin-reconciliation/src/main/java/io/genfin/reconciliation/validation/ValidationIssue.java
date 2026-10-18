package io.genfin.reconciliation.validation;

import io.genfin.api.exception.Severity;

/** One finding raised by a {@link ValidationRule}. */
public record ValidationIssue(String ruleCode, String message, Severity severity) {

  public static ValidationIssue of(String ruleCode, String message, Severity severity) {
    return new ValidationIssue(ruleCode, message, severity);
  }
}
