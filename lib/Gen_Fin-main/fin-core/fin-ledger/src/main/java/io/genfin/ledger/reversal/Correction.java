package io.genfin.ledger.reversal;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.ledger.journal.JournalEntry;

/**
 * A full correcting action: the original entry is reversed ({@link #reversal}) and a new, correct
 * {@link JournalEntry} is posted in its place ({@link #correctingEntry}). Three entries exist side
 * by side once this completes - the untouched original, its reversal, and the correction - none of
 * them ever deleted or rewritten. Produced by {@link
 * io.genfin.ledger.port.reversal.ReverseJournal#correct}.
 */
public record Correction(Reversal reversal, JournalEntry correctingEntry) implements ValueObject {

  public Correction {
    Validate.notNull(reversal, "reversal must not be null.");
    Validate.notNull(correctingEntry, "correctingEntry must not be null.");
  }
}
