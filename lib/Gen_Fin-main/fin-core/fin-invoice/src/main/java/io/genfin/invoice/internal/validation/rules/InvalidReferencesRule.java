package io.genfin.invoice.internal.validation.rules;

import io.genfin.api.exception.Severity;
import io.genfin.invoice.invoice.Invoice;
import io.genfin.invoice.reference.Reference;
import io.genfin.invoice.validation.ValidationContext;
import io.genfin.invoice.validation.ValidationIssue;
import io.genfin.invoice.validation.ValidationRule;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class InvalidReferencesRule implements ValidationRule {

  @Override
  public List<ValidationIssue> apply(Invoice invoice, ValidationContext context) {
    List<ValidationIssue> issues = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (Reference reference : invoice.references().all()) {
      String key = reference.type().code() + ":" + reference.value().value();
      if (!seen.add(key)) {
        issues.add(
            ValidationIssue.of(
                "DUPLICATE_REFERENCE", "Duplicate reference: " + key, Severity.WARNING));
      }
    }
    return issues;
  }
}
