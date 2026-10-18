package io.genfin.ledger.reversal;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.event.OccurredAt;
import io.genfin.api.validation.Validate;
import io.genfin.ledger.journal.JournalEntry;

/**
 * The outcome of reversing one {@link JournalEntry}: the immutable original (its own lifecycle
 * moved forward to {@code REVERSED}, never mutated in content) paired with the new reversing entry
 * that carries the opposite-side lines and references the original. Produced by {@link
 * io.genfin.ledger.port.reversal.ReverseJournal#reverse}.
 */
public record Reversal(
    JournalEntry original,
    JournalEntry reversingEntry,
    ReversalReason reason,
    String memo,
    OccurredAt reversedAt)
    implements ValueObject {

  public Reversal {
    Validate.notNull(original, "original must not be null.");
    Validate.notNull(reversingEntry, "reversingEntry must not be null.");
    Validate.notNull(reason, "reason must not be null.");
    Validate.notNull(reversedAt, "reversedAt must not be null.");
    if (memo == null) {
      memo = "";
    }
  }
}
