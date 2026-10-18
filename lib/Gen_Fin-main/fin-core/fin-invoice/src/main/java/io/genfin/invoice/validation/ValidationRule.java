package io.genfin.invoice.validation;

import io.genfin.invoice.invoice.Invoice;
import java.util.List;

@FunctionalInterface
public interface ValidationRule {

  List<ValidationIssue> apply(Invoice invoice, ValidationContext context);
}
