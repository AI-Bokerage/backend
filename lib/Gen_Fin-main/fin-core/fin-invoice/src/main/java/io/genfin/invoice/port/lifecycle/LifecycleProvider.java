package io.genfin.invoice.port.lifecycle;

import io.genfin.api.port.spi.Extension;
import io.genfin.api.statemachine.StateMachine;
import io.genfin.invoice.lifecycle.InvoiceEvent;
import io.genfin.invoice.lifecycle.InvoiceState;

/**
 * Builds the {@link StateMachine} an {@code Invoice} uses for its lifecycle. Replace to allow
 * custom states/transitions.
 */
public interface LifecycleProvider extends Extension {

  StateMachine<InvoiceState, InvoiceEvent> create(InvoiceState initialState);
}
