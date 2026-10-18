package io.genfin.ledger.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.api.statemachine.StateMachine;
import org.junit.jupiter.api.Test;

class LedgerLifecycleTest {

  @Test
  void standardLifecycleFollowsCreatedValidatedPostedSettledPath() {
    StateMachine<LedgerStatus, LedgerEvent> lifecycle = LedgerLifecycles.created();

    assertThat(lifecycle.fire(StandardLedgerEvent.VALIDATE).isAllowed()).isTrue();
    assertThat(lifecycle.fire(StandardLedgerEvent.POST).isAllowed()).isTrue();
    assertThat(lifecycle.fire(StandardLedgerEvent.SETTLE).isAllowed()).isTrue();
    assertThat(lifecycle.currentState()).isEqualTo(StandardLedgerStatus.SETTLED);
  }

  @Test
  void createdCannotJumpDirectlyToPosted() {
    StateMachine<LedgerStatus, LedgerEvent> lifecycle = LedgerLifecycles.created();

    assertThat(lifecycle.fire(StandardLedgerEvent.POST).isAllowed()).isFalse();
    assertThat(lifecycle.currentState()).isEqualTo(StandardLedgerStatus.CREATED);
  }

  @Test
  void postedOrSettledCanBeReversedOrAdjustedWithoutLosingHistory() {
    StateMachine<LedgerStatus, LedgerEvent> posted = LedgerLifecycles.created();
    posted.fire(StandardLedgerEvent.VALIDATE);
    posted.fire(StandardLedgerEvent.POST);

    assertThat(posted.fire(StandardLedgerEvent.ADJUST).isAllowed()).isTrue();
    assertThat(posted.currentState()).isEqualTo(StandardLedgerStatus.ADJUSTED);

    StateMachine<LedgerStatus, LedgerEvent> settled = LedgerLifecycles.created();
    settled.fire(StandardLedgerEvent.VALIDATE);
    settled.fire(StandardLedgerEvent.POST);
    settled.fire(StandardLedgerEvent.SETTLE);
    assertThat(settled.fire(StandardLedgerEvent.REVERSE).isAllowed()).isTrue();
    assertThat(settled.currentState()).isEqualTo(StandardLedgerStatus.REVERSED);
  }

  @Test
  void aFailedValidationCanBeRetriedBackIntoValidated() {
    StateMachine<LedgerStatus, LedgerEvent> lifecycle = LedgerLifecycles.created();
    lifecycle.fire(StandardLedgerEvent.FAIL);

    assertThat(lifecycle.currentState()).isEqualTo(StandardLedgerStatus.FAILED);
    assertThat(lifecycle.fire(StandardLedgerEvent.RETRY).isAllowed()).isTrue();
    assertThat(lifecycle.currentState()).isEqualTo(StandardLedgerStatus.VALIDATED);
  }

  @Test
  void archivedAndReversedAreTerminal() {
    StateMachine<LedgerStatus, LedgerEvent> lifecycle = LedgerLifecycles.created();
    lifecycle.fire(StandardLedgerEvent.VALIDATE);
    lifecycle.fire(StandardLedgerEvent.POST);
    lifecycle.fire(StandardLedgerEvent.REVERSE);

    assertThat(lifecycle.fire(StandardLedgerEvent.ARCHIVE).isAllowed()).isTrue();
    assertThat(lifecycle.currentState()).isEqualTo(StandardLedgerStatus.ARCHIVED);
    assertThat(lifecycle.fire(StandardLedgerEvent.POST).isAllowed()).isFalse();
  }
}
