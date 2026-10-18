package io.genfin.invoice.internal.validation;

import io.genfin.invoice.invoice.Invoice;
import io.genfin.invoice.port.validation.InvoiceValidator;
import io.genfin.invoice.validation.ValidationContext;
import io.genfin.invoice.validation.ValidationIssue;
import io.genfin.invoice.validation.ValidationResult;
import io.genfin.invoice.validation.ValidationRule;
import java.util.ArrayList;
import java.util.List;

public final class DefaultInvoiceValidator implements InvoiceValidator {

  private final List<ValidationRule> rules;

  public DefaultInvoiceValidator(List<ValidationRule> rules) {
    this.rules = List.copyOf(rules);
  }

  @Override
  public ValidationResult validate(Invoice invoice, ValidationContext context) {
    List<ValidationIssue> issues = new ArrayList<>();
    for (ValidationRule rule : rules) {
      issues.addAll(rule.apply(invoice, context));
    }
    return new ValidationResult(issues);
  }
}
