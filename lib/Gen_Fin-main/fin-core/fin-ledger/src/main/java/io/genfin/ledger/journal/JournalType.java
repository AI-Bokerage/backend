package io.genfin.ledger.journal;

/**
 * The *kind* of a {@link JournalEntry} (standard posting, reversal, adjustment, ...). Mirrors
 * {@code io.genfin.refund.refund.RefundType}: a structural taxonomy, not a business-account naming
 * concept, so it needs no registry - just an interface applications may extend beyond {@link
 * StandardJournalType} if they need to.
 */
public interface JournalType {

  String code();
}
