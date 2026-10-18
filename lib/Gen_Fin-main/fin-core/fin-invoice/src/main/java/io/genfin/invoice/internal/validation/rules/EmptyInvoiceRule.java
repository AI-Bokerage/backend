package io.genfin.invoice.internal.validation.rules;

import io.genfin.api.exception.Severity;
import io.genfin.invoice.invoice.Invoice;
import io.genfin.invoice.validation.ValidationContext;
import io.genfin.invoice.validation.ValidationIssue;
import io.genfin.invoice.validation.ValidationRule;
import java.util.List;

public final class EmptyInvoiceRule implements ValidationRule {

  @Override
  public List<ValidationIssue> apply(Invoice invoice, ValidationContext context) {
    if (invoice.lines().isEmpty()) {
      return List.of(ValidationIssue.of("EMPTY_INVOICE", "Invoice has no lines.", Severity.ERROR));
    }
    return List.of();
  }
}
