package io.genfin.refund.validation;

import io.genfin.api.exception.Severity;

public record ValidationIssue(String ruleCode, String message, Severity severity) {

  public static ValidationIssue of(String ruleCode, String message, Severity severity) {
    return new ValidationIssue(ruleCode, message, severity);
  }
}
