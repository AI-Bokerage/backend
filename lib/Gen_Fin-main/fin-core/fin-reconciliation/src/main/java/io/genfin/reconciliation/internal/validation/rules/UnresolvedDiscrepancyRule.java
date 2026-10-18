package io.genfin.reconciliation.internal.validation.rules;

import io.genfin.api.exception.Severity;
import io.genfin.reconciliation.reconciliation.Reconciliation;
import io.genfin.reconciliation.validation.ValidationContext;
import io.genfin.reconciliation.validation.ValidationIssue;
import io.genfin.reconciliation.validation.ValidationRule;
import java.util.List;

/**
 * Defense in depth: {@code Reconciliation} already refuses full reconciliation while open
 * discrepancies remain — this rule lets callers surface the same finding before attempting it.
 */
public final class UnresolvedDiscrepancyRule implements ValidationRule {

  @Override
  public List<ValidationIssue> apply(Reconciliation reconciliation, ValidationContext context) {
    if (!reconciliation.discrepancies().isEmpty()) {
      return List.of(
          ValidationIssue.of(
              "UNRESOLVED_DISCREPANCY",
              "Reconciliation has unresolved discrepancies.",
              Severity.ERROR));
    }
    return List.of();
  }
}
