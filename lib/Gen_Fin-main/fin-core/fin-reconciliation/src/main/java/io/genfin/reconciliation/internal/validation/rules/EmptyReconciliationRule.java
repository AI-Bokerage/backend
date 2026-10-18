package io.genfin.reconciliation.internal.validation.rules;

import io.genfin.api.exception.Severity;
import io.genfin.reconciliation.reconciliation.Reconciliation;
import io.genfin.reconciliation.validation.ValidationContext;
import io.genfin.reconciliation.validation.ValidationIssue;
import io.genfin.reconciliation.validation.ValidationRule;
import java.util.List;

/** A reconciliation with no items to compare is almost certainly a caller mistake. */
public final class EmptyReconciliationRule implements ValidationRule {

  @Override
  public List<ValidationIssue> apply(Reconciliation reconciliation, ValidationContext context) {
    if (reconciliation.items().isEmpty()) {
      return List.of(
          ValidationIssue.of(
              "EMPTY_RECONCILIATION", "Reconciliation has no items to compare.", Severity.WARNING));
    }
    return List.of();
  }
}
