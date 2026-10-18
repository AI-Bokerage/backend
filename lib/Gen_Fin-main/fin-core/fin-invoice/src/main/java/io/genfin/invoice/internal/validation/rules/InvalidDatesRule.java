package io.genfin.invoice.internal.validation.rules;

import io.genfin.api.exception.Severity;
import io.genfin.invoice.invoice.Invoice;
import io.genfin.invoice.validation.ValidationContext;
import io.genfin.invoice.validation.ValidationIssue;
import io.genfin.invoice.validation.ValidationRule;
import java.util.ArrayList;
import java.util.List;

public final class InvalidDatesRule implements ValidationRule {

  @Override
  public List<ValidationIssue> apply(Invoice invoice, ValidationContext context) {
    List<ValidationIssue> issues = new ArrayList<>();
    var dates = invoice.dates();
    if (dates.dueDate() == null) {
      issues.add(
          ValidationIssue.of("MISSING_DUE_DATE", "Invoice has no due date.", Severity.ERROR));
    } else if (dates.issueDate() != null && dates.dueDate().isBefore(dates.issueDate())) {
      issues.add(
          ValidationIssue.of(
              "DUE_BEFORE_ISSUE", "Due date is before the issue date.", Severity.ERROR));
    }
    return issues;
  }
}
