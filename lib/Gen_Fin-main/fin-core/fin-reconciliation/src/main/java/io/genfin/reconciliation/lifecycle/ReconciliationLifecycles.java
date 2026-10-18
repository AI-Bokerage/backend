package io.genfin.reconciliation.lifecycle;

import io.genfin.api.statemachine.StateMachine;
import io.genfin.reconciliation.internal.lifecycle.DefaultReconciliationLifecycleProvider;
import io.genfin.reconciliation.port.lifecycle.ReconciliationLifecycleProvider;

public final class ReconciliationLifecycles {

  private static final ReconciliationLifecycleProvider STANDARD =
      new DefaultReconciliationLifecycleProvider();

  private ReconciliationLifecycles() {}

  public static ReconciliationLifecycleProvider standard() {
    return STANDARD;
  }

  public static StateMachine<ReconciliationStatus, ReconciliationEvent> created() {
    return STANDARD.create(StandardReconciliationStatus.CREATED);
  }
}
