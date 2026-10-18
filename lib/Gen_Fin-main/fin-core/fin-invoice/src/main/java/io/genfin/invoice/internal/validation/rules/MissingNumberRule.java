package io.genfin.invoice.internal.validation.rules;

import io.genfin.api.exception.Severity;
import io.genfin.invoice.invoice.Invoice;
import io.genfin.invoice.lifecycle.StandardInvoiceState;
import io.genfin.invoice.validation.ValidationContext;
import io.genfin.invoice.validation.ValidationIssue;
import io.genfin.invoice.validation.ValidationRule;
import java.util.List;

public final class MissingNumberRule implements ValidationRule {

  @Override
  public List<ValidationIssue> apply(Invoice invoice, ValidationContext context) {
    if (invoice.status() != StandardInvoiceState.DRAFT && invoice.number().isEmpty()) {
      return List.of(
          ValidationIssue.of(
              "MISSING_NUMBER", "Non-draft invoice has no invoice number.", Severity.ERROR));
    }
    return List.of();
  }
}
