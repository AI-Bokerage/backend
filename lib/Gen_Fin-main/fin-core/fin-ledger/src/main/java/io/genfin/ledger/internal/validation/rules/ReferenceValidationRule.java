package io.genfin.ledger.internal.validation.rules;

import io.genfin.api.exception.Severity;
import io.genfin.ledger.journal.JournalEntry;
import io.genfin.ledger.validation.ValidationContext;
import io.genfin.ledger.validation.ValidationIssue;
import io.genfin.ledger.validation.ValidationRule;
import java.util.List;

/**
 * A {@link JournalEntry} with no {@link io.genfin.refund.reference.Reference} back to the financial
 * fact that caused it is almost certainly a caller mistake - the entry becomes untraceable to the
 * Invoice/Payment/Refund/Reconciliation record it originated from. Mirrors {@code
 * io.genfin.reconciliation.internal.validation.rules.EmptyReconciliationRule}.
 */
public final class ReferenceValidationRule implements ValidationRule {

  @Override
  public List<ValidationIssue> apply(JournalEntry entry, ValidationContext context) {
    if (entry.references().isEmpty()) {
      return List.of(
          ValidationIssue.of(
              "MISSING_REFERENCE",
              "journal entry has no reference to the fact that caused it.",
              Severity.WARNING));
    }
    return List.of();
  }
}
