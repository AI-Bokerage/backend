package io.genfin.reconciliation.lifecycle;

/**
 * An event that drives a {@link io.genfin.reconciliation.reconciliation.Reconciliation} lifecycle
 * transition.
 */
public interface ReconciliationEvent {

  String code();
}
