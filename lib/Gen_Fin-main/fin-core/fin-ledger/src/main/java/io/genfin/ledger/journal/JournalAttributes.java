package io.genfin.ledger.journal;

import io.genfin.api.domain.ValueObject;

/**
 * Structural posting flags for a {@link JournalEntry}. These govern *how* the entry participates in
 * posting/reversal, not what it means - that belongs to {@link JournalType} / {@link
 * JournalMetadata}. Mirrors {@code io.genfin.ledger.account.AccountAttributes}.
 */
public record JournalAttributes(
    boolean systemGenerated, boolean reversible, boolean requiresApproval) implements ValueObject {

  /** A normal, manually reversible entry. */
  public static JournalAttributes standard() {
    return new JournalAttributes(false, true, false);
  }

  /** An entry produced by the Posting Engine itself rather than an operator. */
  public static JournalAttributes systemPosted() {
    return new JournalAttributes(true, true, false);
  }
}
