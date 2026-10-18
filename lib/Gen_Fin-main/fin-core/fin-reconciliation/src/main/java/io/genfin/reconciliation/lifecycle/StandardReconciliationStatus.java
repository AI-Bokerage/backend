package io.genfin.reconciliation.lifecycle;

/** The standard reconciliation lifecycle states. */
public enum StandardReconciliationStatus implements ReconciliationStatus {
  CREATED,
  COLLECTING,
  MATCHING,
  ANALYZING,
  RECONCILED,
  PARTIALLY_RECONCILED,
  FAILED,
  CANCELLED;

  @Override
  public String code() {
    return name();
  }
}
