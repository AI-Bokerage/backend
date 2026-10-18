package io.genfin.ledger.port.lifecycle;

import io.genfin.api.port.spi.Extension;
import io.genfin.api.statemachine.StateMachine;
import io.genfin.ledger.lifecycle.LedgerEvent;
import io.genfin.ledger.lifecycle.LedgerStatus;

/** SPI for building the {@link StateMachine} that drives a journal entry's lifecycle. */
public interface LedgerLifecycleProvider extends Extension {

  StateMachine<LedgerStatus, LedgerEvent> create(LedgerStatus initialState);
}
