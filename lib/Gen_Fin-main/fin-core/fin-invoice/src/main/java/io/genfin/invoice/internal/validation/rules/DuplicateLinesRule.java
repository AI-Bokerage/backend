package io.genfin.invoice.internal.validation.rules;

import io.genfin.api.exception.Severity;
import io.genfin.invoice.invoice.Invoice;
import io.genfin.invoice.line.InvoiceLine;
import io.genfin.invoice.validation.ValidationContext;
import io.genfin.invoice.validation.ValidationIssue;
import io.genfin.invoice.validation.ValidationRule;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DuplicateLinesRule implements ValidationRule {

  @Override
  public List<ValidationIssue> apply(Invoice invoice, ValidationContext context) {
    List<ValidationIssue> issues = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (InvoiceLine line : invoice.lines()) {
      String key = line.description() + "|" + line.quantity() + "|" + line.unitPrice();
      if (!seen.add(key)) {
        issues.add(
            ValidationIssue.of(
                "DUPLICATE_LINE",
                "Duplicate line detected: " + line.description(),
                Severity.WARNING));
      }
    }
    return issues;
  }
}
