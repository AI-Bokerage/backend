package io.genfin.reconciliation.lifecycle;

/** The standard reconciliation lifecycle events. */
public enum StandardReconciliationEvent implements ReconciliationEvent {
  COLLECT,
  MATCH,
  ANALYZE,
  RECONCILE,
  PARTIALLY_RECONCILE,
  FAIL,
  CANCEL;

  @Override
  public String code() {
    return name();
  }
}
