package io.genfin.invoice.internal.validation.rules;

import io.genfin.api.exception.Severity;
import io.genfin.invoice.invoice.Invoice;
import io.genfin.invoice.line.InvoiceLine;
import io.genfin.invoice.validation.ValidationContext;
import io.genfin.invoice.validation.ValidationIssue;
import io.genfin.invoice.validation.ValidationRule;
import java.util.ArrayList;
import java.util.List;

public final class LineCurrencyMismatchRule implements ValidationRule {

  @Override
  public List<ValidationIssue> apply(Invoice invoice, ValidationContext context) {
    List<ValidationIssue> issues = new ArrayList<>();
    for (InvoiceLine line : invoice.lines()) {
      if (!line.unitPrice().currency().equals(invoice.currency())) {
        issues.add(
            ValidationIssue.of(
                "CURRENCY_MISMATCH",
                "Line currency "
                    + line.unitPrice().currency().code()
                    + " does not match invoice currency "
                    + invoice.currency().code()
                    + ".",
                Severity.ERROR));
      }
    }
    return issues;
  }
}
