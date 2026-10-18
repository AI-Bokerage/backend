package io.genfin.reconciliation.internal.lifecycle;

import static io.genfin.reconciliation.lifecycle.StandardReconciliationEvent.ANALYZE;
import static io.genfin.reconciliation.lifecycle.StandardReconciliationEvent.CANCEL;
import static io.genfin.reconciliation.lifecycle.StandardReconciliationEvent.COLLECT;
import static io.genfin.reconciliation.lifecycle.StandardReconciliationEvent.FAIL;
import static io.genfin.reconciliation.lifecycle.StandardReconciliationEvent.MATCH;
import static io.genfin.reconciliation.lifecycle.StandardReconciliationEvent.PARTIALLY_RECONCILE;
import static io.genfin.reconciliation.lifecycle.StandardReconciliationEvent.RECONCILE;
import static io.genfin.reconciliation.lifecycle.StandardReconciliationStatus.ANALYZING;
import static io.genfin.reconciliation.lifecycle.StandardReconciliationStatus.CANCELLED;
import static io.genfin.reconciliation.lifecycle.StandardReconciliationStatus.COLLECTING;
import static io.genfin.reconciliation.lifecycle.StandardReconciliationStatus.CREATED;
import static io.genfin.reconciliation.lifecycle.StandardReconciliationStatus.FAILED;
import static io.genfin.reconciliation.lifecycle.StandardReconciliationStatus.MATCHING;
import static io.genfin.reconciliation.lifecycle.StandardReconciliationStatus.PARTIALLY_RECONCILED;
import static io.genfin.reconciliation.lifecycle.StandardReconciliationStatus.RECONCILED;

import io.genfin.api.statemachine.StateMachine;
import io.genfin.api.statemachine.StateMachines;
import io.genfin.api.statemachine.Transition;
import io.genfin.reconciliation.lifecycle.ReconciliationEvent;
import io.genfin.reconciliation.lifecycle.ReconciliationStatus;
import io.genfin.reconciliation.port.lifecycle.ReconciliationLifecycleProvider;
import java.util.List;

/**
 * The standard reconciliation lifecycle: Created → Collecting → Matching → Analyzing →
 * (Partially)Reconciled, with fail/cancel/retry side paths.
 */
public final class DefaultReconciliationLifecycleProvider
    implements ReconciliationLifecycleProvider {

  @Override
  public StateMachine<ReconciliationStatus, ReconciliationEvent> create(
      ReconciliationStatus initialState) {
    return StateMachines.of(initialState, transitions());
  }

  private static List<Transition<ReconciliationStatus, ReconciliationEvent>> transitions() {
    return List.of(
        new Transition<>(CREATED, COLLECT, COLLECTING),
        new Transition<>(CREATED, CANCEL, CANCELLED),
        new Transition<>(COLLECTING, MATCH, MATCHING),
        new Transition<>(COLLECTING, CANCEL, CANCELLED),
        new Transition<>(MATCHING, ANALYZE, ANALYZING),
        new Transition<>(MATCHING, CANCEL, CANCELLED),
        new Transition<>(ANALYZING, RECONCILE, RECONCILED),
        new Transition<>(ANALYZING, PARTIALLY_RECONCILE, PARTIALLY_RECONCILED),
        new Transition<>(ANALYZING, FAIL, FAILED),
        new Transition<>(ANALYZING, CANCEL, CANCELLED),
        new Transition<>(PARTIALLY_RECONCILED, RECONCILE, RECONCILED),
        new Transition<>(PARTIALLY_RECONCILED, FAIL, FAILED),
        new Transition<>(FAILED, COLLECT, COLLECTING));
  }
}
