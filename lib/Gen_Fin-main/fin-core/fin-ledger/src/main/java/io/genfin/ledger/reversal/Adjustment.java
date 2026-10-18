package io.genfin.ledger.reversal;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.event.OccurredAt;
import io.genfin.api.validation.Validate;
import io.genfin.ledger.journal.JournalEntry;

/**
 * The outcome of adjusting one {@link JournalEntry}: the original (its own lifecycle moved forward
 * to {@code ADJUSTED}, never mutated in content) paired with the new adjusting entry carrying the
 * corrective lines and a reference back to the original. Unlike a {@link Reversal}, the adjusting
 * entry's lines are not a mechanical swap of the original's - they are whatever corrective legs the
 * caller supplies. Produced by {@link io.genfin.ledger.port.reversal.ReverseJournal#adjust}.
 */
public record Adjustment(
    JournalEntry adjusted,
    JournalEntry adjustingEntry,
    ReversalReason reason,
    String memo,
    OccurredAt adjustedAt)
    implements ValueObject {

  public Adjustment {
    Validate.notNull(adjusted, "adjusted must not be null.");
    Validate.notNull(adjustingEntry, "adjustingEntry must not be null.");
    Validate.notNull(reason, "reason must not be null.");
    Validate.notNull(adjustedAt, "adjustedAt must not be null.");
    if (memo == null) {
      memo = "";
    }
  }
}
