package io.genfin.ledger.internal.lifecycle;

import static io.genfin.ledger.lifecycle.StandardLedgerEvent.ADJUST;
import static io.genfin.ledger.lifecycle.StandardLedgerEvent.ARCHIVE;
import static io.genfin.ledger.lifecycle.StandardLedgerEvent.FAIL;
import static io.genfin.ledger.lifecycle.StandardLedgerEvent.POST;
import static io.genfin.ledger.lifecycle.StandardLedgerEvent.RETRY;
import static io.genfin.ledger.lifecycle.StandardLedgerEvent.REVERSE;
import static io.genfin.ledger.lifecycle.StandardLedgerEvent.SETTLE;
import static io.genfin.ledger.lifecycle.StandardLedgerEvent.VALIDATE;
import static io.genfin.ledger.lifecycle.StandardLedgerStatus.ADJUSTED;
import static io.genfin.ledger.lifecycle.StandardLedgerStatus.ARCHIVED;
import static io.genfin.ledger.lifecycle.StandardLedgerStatus.CREATED;
import static io.genfin.ledger.lifecycle.StandardLedgerStatus.FAILED;
import static io.genfin.ledger.lifecycle.StandardLedgerStatus.POSTED;
import static io.genfin.ledger.lifecycle.StandardLedgerStatus.REVERSED;
import static io.genfin.ledger.lifecycle.StandardLedgerStatus.SETTLED;
import static io.genfin.ledger.lifecycle.StandardLedgerStatus.VALIDATED;

import io.genfin.api.statemachine.StateMachine;
import io.genfin.api.statemachine.StateMachines;
import io.genfin.api.statemachine.Transition;
import io.genfin.ledger.lifecycle.LedgerEvent;
import io.genfin.ledger.lifecycle.LedgerStatus;
import io.genfin.ledger.port.lifecycle.LedgerLifecycleProvider;
import java.util.List;

/**
 * The standard journal entry lifecycle: Created → Validated → Posted → Settled, with
 * reverse/adjust/archive/fail side paths. Reversal and adjustment never remove a prior state - they
 * are additional forward transitions applied on top of an immutable history.
 */
public final class DefaultLedgerLifecycleProvider implements LedgerLifecycleProvider {

  @Override
  public StateMachine<LedgerStatus, LedgerEvent> create(LedgerStatus initialState) {
    return StateMachines.of(initialState, transitions());
  }

  private static List<Transition<LedgerStatus, LedgerEvent>> transitions() {
    return List.of(
        new Transition<>(CREATED, VALIDATE, VALIDATED),
        new Transition<>(CREATED, FAIL, FAILED),
        new Transition<>(VALIDATED, POST, POSTED),
        new Transition<>(VALIDATED, FAIL, FAILED),
        new Transition<>(POSTED, SETTLE, SETTLED),
        new Transition<>(POSTED, REVERSE, REVERSED),
        new Transition<>(POSTED, ADJUST, ADJUSTED),
        new Transition<>(SETTLED, REVERSE, REVERSED),
        new Transition<>(SETTLED, ADJUST, ADJUSTED),
        new Transition<>(SETTLED, ARCHIVE, ARCHIVED),
        new Transition<>(ADJUSTED, ARCHIVE, ARCHIVED),
        new Transition<>(ADJUSTED, REVERSE, REVERSED),
        new Transition<>(REVERSED, ARCHIVE, ARCHIVED),
        new Transition<>(FAILED, RETRY, VALIDATED));
  }
}
