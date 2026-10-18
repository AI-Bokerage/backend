package io.genfin.ledger.lifecycle;

/** The standard journal entry lifecycle states. */
public enum StandardLedgerStatus implements LedgerStatus {
  CREATED,
  VALIDATED,
  POSTED,
  SETTLED,
  REVERSED,
  ADJUSTED,
  ARCHIVED,
  FAILED;

  @Override
  public String code() {
    return name();
  }
}
