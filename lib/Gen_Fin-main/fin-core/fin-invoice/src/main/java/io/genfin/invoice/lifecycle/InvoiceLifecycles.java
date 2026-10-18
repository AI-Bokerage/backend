package io.genfin.invoice.lifecycle;

import io.genfin.api.statemachine.StateMachine;
import io.genfin.invoice.internal.lifecycle.DefaultLifecycleProvider;
import io.genfin.invoice.port.lifecycle.LifecycleProvider;

/** Factory for {@link StateMachine} instances governing invoice lifecycle. */
public final class InvoiceLifecycles {

  private static final LifecycleProvider STANDARD = new DefaultLifecycleProvider();

  private InvoiceLifecycles() {}

  public static LifecycleProvider standard() {
    return STANDARD;
  }

  public static StateMachine<InvoiceState, InvoiceEvent> draft() {
    return STANDARD.create(StandardInvoiceState.DRAFT);
  }
}
