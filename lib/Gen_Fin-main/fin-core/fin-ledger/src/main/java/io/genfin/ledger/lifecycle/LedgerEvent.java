package io.genfin.ledger.lifecycle;

/** An event that drives a {@link io.genfin.ledger.journal.JournalEntry} lifecycle transition. */
public interface LedgerEvent {

  String code();
}
