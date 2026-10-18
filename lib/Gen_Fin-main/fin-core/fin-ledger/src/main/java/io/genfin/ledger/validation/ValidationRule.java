package io.genfin.ledger.validation;

import io.genfin.ledger.journal.JournalEntry;
import java.util.List;

/**
 * One composable check over a {@link JournalEntry}. A {@link
 * io.genfin.ledger.port.validation.JournalValidator} runs every registered rule and never
 * short-circuits, so a single validation pass surfaces every issue at once.
 */
@FunctionalInterface
public interface ValidationRule {

  List<ValidationIssue> apply(JournalEntry entry, ValidationContext context);
}
