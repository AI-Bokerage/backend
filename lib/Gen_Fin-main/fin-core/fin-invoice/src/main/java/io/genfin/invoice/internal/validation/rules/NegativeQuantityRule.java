package io.genfin.invoice.internal.validation.rules;

import io.genfin.api.exception.Severity;
import io.genfin.invoice.invoice.Invoice;
import io.genfin.invoice.line.InvoiceLine;
import io.genfin.invoice.validation.ValidationContext;
import io.genfin.invoice.validation.ValidationIssue;
import io.genfin.invoice.validation.ValidationRule;
import java.util.ArrayList;
import java.util.List;

/** Defense in depth: {@code InvoiceLine} already rejects non-positive quantity at construction. */
public final class NegativeQuantityRule implements ValidationRule {

  @Override
  public List<ValidationIssue> apply(Invoice invoice, ValidationContext context) {
    List<ValidationIssue> issues = new ArrayList<>();
    for (InvoiceLine line : invoice.lines()) {
      if (line.quantity().signum() <= 0) {
        issues.add(
            ValidationIssue.of(
                "NEGATIVE_QUANTITY",
                "Line has non-positive quantity: " + line.description(),
                Severity.ERROR));
      }
    }
    return issues;
  }
}
