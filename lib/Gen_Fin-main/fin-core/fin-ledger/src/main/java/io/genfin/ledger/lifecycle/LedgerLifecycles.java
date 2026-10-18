package io.genfin.ledger.lifecycle;

import io.genfin.api.statemachine.StateMachine;
import io.genfin.ledger.internal.lifecycle.DefaultLedgerLifecycleProvider;
import io.genfin.ledger.port.lifecycle.LedgerLifecycleProvider;

/** Access point for the standard {@link LedgerLifecycleProvider}. */
public final class LedgerLifecycles {

  private static final LedgerLifecycleProvider STANDARD = new DefaultLedgerLifecycleProvider();

  private LedgerLifecycles() {}

  public static LedgerLifecycleProvider standard() {
    return STANDARD;
  }

  public static StateMachine<LedgerStatus, LedgerEvent> created() {
    return STANDARD.create(StandardLedgerStatus.CREATED);
  }
}
