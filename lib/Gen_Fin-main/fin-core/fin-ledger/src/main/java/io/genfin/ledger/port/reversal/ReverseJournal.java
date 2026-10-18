package io.genfin.ledger.port.reversal;

import io.genfin.api.port.spi.Extension;
import io.genfin.ledger.journal.JournalEntry;
import io.genfin.ledger.posting.PostingEntry;
import io.genfin.ledger.reversal.Adjustment;
import io.genfin.ledger.reversal.Correction;
import io.genfin.ledger.reversal.Reversal;
import io.genfin.ledger.reversal.ReversalReason;
import java.time.Instant;
import java.util.List;

/**
 * The Reversal Engine: the single entry point for correcting a posted {@link JournalEntry} without
 * ever deleting or mutating it. Every operation here only ever moves the original entry's own
 * lifecycle forward (mirroring {@link JournalEntry#reverse()} / {@link JournalEntry#adjust()}) and
 * creates a brand new entry alongside it - the original's lines are never touched.
 */
public interface ReverseJournal extends Extension {

  /**
   * Reverses {@code original} in full: moves its lifecycle to {@code REVERSED} and returns a new
   * {@code REVERSAL}-type entry whose lines are the exact opposite side of the original's, for the
   * same accounts and amounts.
   */
  Reversal reverse(JournalEntry original, ReversalReason reason, String memo, Instant reversedAt);

  /**
   * Adjusts {@code original}: moves its lifecycle to {@code ADJUSTED} and returns a new {@code
   * ADJUSTMENT}-type entry posting {@code adjustingEntries} - the corrective legs the caller
   * supplies, not a mechanical swap of the original's lines.
   */
  Adjustment adjust(
      JournalEntry original,
      List<PostingEntry> adjustingEntries,
      ReversalReason reason,
      String memo,
      Instant adjustedAt);

  /**
   * A full correction: reverses {@code original} (see {@link #reverse}) and posts a new {@code
   * STANDARD}-type entry carrying {@code correctedEntries} in its place.
   */
  Correction correct(
      JournalEntry original,
      List<PostingEntry> correctedEntries,
      ReversalReason reason,
      String memo,
      Instant correctedAt);
}
