package io.genfin.refund.internal.validation;

import io.genfin.refund.port.validation.RefundValidator;
import io.genfin.refund.refund.Refund;
import io.genfin.refund.validation.ValidationContext;
import io.genfin.refund.validation.ValidationIssue;
import io.genfin.refund.validation.ValidationResult;
import io.genfin.refund.validation.ValidationRule;
import java.util.ArrayList;
import java.util.List;

/** Runs every rule and collects all issues — never short-circuits on the first failure. */
public final class DefaultRefundValidator implements RefundValidator {

  private final List<ValidationRule> rules;

  public DefaultRefundValidator(List<ValidationRule> rules) {
    this.rules = List.copyOf(rules);
  }

  @Override
  public ValidationResult validate(Refund refund, ValidationContext context) {
    List<ValidationIssue> issues = new ArrayList<>();
    for (ValidationRule rule : rules) {
      issues.addAll(rule.apply(refund, context));
    }
    return new ValidationResult(issues);
  }
}
