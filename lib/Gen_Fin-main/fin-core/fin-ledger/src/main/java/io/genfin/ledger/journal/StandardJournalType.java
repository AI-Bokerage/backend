package io.genfin.ledger.journal;

/** The standard journal entry kinds. Mirrors {@code io.genfin.refund.refund.StandardRefundType}. */
public enum StandardJournalType implements JournalType {
  STANDARD,
  REVERSAL,
  ADJUSTMENT,
  OPENING,
  CLOSING;

  @Override
  public String code() {
    return name();
  }
}
