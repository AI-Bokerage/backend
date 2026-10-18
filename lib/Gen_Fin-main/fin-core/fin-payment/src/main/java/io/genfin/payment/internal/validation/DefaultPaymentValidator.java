package io.genfin.payment.internal.validation;

import io.genfin.payment.payment.Payment;
import io.genfin.payment.port.validation.PaymentValidator;
import io.genfin.payment.validation.ValidationContext;
import io.genfin.payment.validation.ValidationIssue;
import io.genfin.payment.validation.ValidationResult;
import io.genfin.payment.validation.ValidationRule;
import java.util.ArrayList;
import java.util.List;

public final class DefaultPaymentValidator implements PaymentValidator {

  private final List<ValidationRule> rules;

  public DefaultPaymentValidator(List<ValidationRule> rules) {
    this.rules = List.copyOf(rules);
  }

  @Override
  public ValidationResult validate(Payment payment, ValidationContext context) {
    List<ValidationIssue> issues = new ArrayList<>();
    for (ValidationRule rule : rules) {
      issues.addAll(rule.apply(payment, context));
    }
    return new ValidationResult(issues);
  }
}
