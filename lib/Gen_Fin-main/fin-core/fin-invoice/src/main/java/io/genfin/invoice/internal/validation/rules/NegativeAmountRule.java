package io.genfin.invoice.internal.validation.rules;

import io.genfin.api.exception.Severity;
import io.genfin.invoice.invoice.Invoice;
import io.genfin.invoice.line.InvoiceLine;
import io.genfin.invoice.validation.ValidationContext;
import io.genfin.invoice.validation.ValidationIssue;
import io.genfin.invoice.validation.ValidationRule;
import java.util.ArrayList;
import java.util.List;

/** Defense in depth: {@code InvoiceLine} already rejects negative unit price at construction. */
public final class NegativeAmountRule implements ValidationRule {

  @Override
  public List<ValidationIssue> apply(Invoice invoice, ValidationContext context) {
    List<ValidationIssue> issues = new ArrayList<>();
    for (InvoiceLine line : invoice.lines()) {
      if (line.unitPrice().isNegative()) {
        issues.add(
            ValidationIssue.of(
                "NEGATIVE_AMOUNT",
                "Line has a negative unit price: " + line.description(),
                Severity.ERROR));
      }
    }
    return issues;
  }
}
