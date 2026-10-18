package io.genfin.ledger.lifecycle;

/** The standard journal entry lifecycle events. */
public enum StandardLedgerEvent implements LedgerEvent {
  VALIDATE,
  POST,
  SETTLE,
  REVERSE,
  ADJUST,
  ARCHIVE,
  FAIL,
  RETRY;

  @Override
  public String code() {
    return name();
  }
}
