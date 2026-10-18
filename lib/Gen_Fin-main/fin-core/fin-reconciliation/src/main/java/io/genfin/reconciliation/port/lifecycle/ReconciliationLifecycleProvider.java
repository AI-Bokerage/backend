package io.genfin.reconciliation.port.lifecycle;

import io.genfin.api.port.spi.Extension;
import io.genfin.api.statemachine.StateMachine;
import io.genfin.reconciliation.lifecycle.ReconciliationEvent;
import io.genfin.reconciliation.lifecycle.ReconciliationStatus;

/** SPI for building the {@link StateMachine} that drives a reconciliation's lifecycle. */
public interface ReconciliationLifecycleProvider extends Extension {

  StateMachine<ReconciliationStatus, ReconciliationEvent> create(ReconciliationStatus initialState);
}
