package io.genfin.dunning.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.api.statemachine.StateMachine;
import org.junit.jupiter.api.Test;

class DunningCaseLifecycleTest {

  @Test
  void standardLifecycleFollowsActiveWaitingRetryingEscalatedPath() {
    StateMachine<DunningCaseStatus, DunningCaseEvent> lifecycle = DunningCaseLifecycles.created();

    assertThat(lifecycle.fire(StandardDunningCaseEvent.ACTIVATE).isAllowed()).isTrue();
    assertThat(lifecycle.fire(StandardDunningCaseEvent.SCHEDULE).isAllowed()).isTrue();
    assertThat(lifecycle.fire(StandardDunningCaseEvent.RETRY).isAllowed()).isTrue();
    assertThat(lifecycle.fire(StandardDunningCaseEvent.ESCALATE).isAllowed()).isTrue();
    assertThat(lifecycle.currentState()).isEqualTo(StandardDunningCaseStatus.ESCALATED);
  }

  @Test
  void createdCannotJumpDirectlyToRetrying() {
    StateMachine<DunningCaseStatus, DunningCaseEvent> lifecycle = DunningCaseLifecycles.created();

    assertThat(lifecycle.fire(StandardDunningCaseEvent.RETRY).isAllowed()).isFalse();
    assertThat(lifecycle.currentState()).isEqualTo(StandardDunningCaseStatus.CREATED);
  }

  @Test
  void pausedCaseCanBeResumedBackToActive() {
    StateMachine<DunningCaseStatus, DunningCaseEvent> lifecycle = DunningCaseLifecycles.created();
    lifecycle.fire(StandardDunningCaseEvent.ACTIVATE);
    lifecycle.fire(StandardDunningCaseEvent.PAUSE);

    assertThat(lifecycle.currentState()).isEqualTo(StandardDunningCaseStatus.PAUSED);
    assertThat(lifecycle.fire(StandardDunningCaseEvent.RESUME).isAllowed()).isTrue();
    assertThat(lifecycle.currentState()).isEqualTo(StandardDunningCaseStatus.ACTIVE);
  }

  @Test
  void failedCaseCanOnlyBeWrittenOff() {
    StateMachine<DunningCaseStatus, DunningCaseEvent> lifecycle = DunningCaseLifecycles.created();
    lifecycle.fire(StandardDunningCaseEvent.ACTIVATE);
    lifecycle.fire(StandardDunningCaseEvent.RETRY);
    lifecycle.fire(StandardDunningCaseEvent.FAIL);

    assertThat(lifecycle.currentState()).isEqualTo(StandardDunningCaseStatus.FAILED);
    assertThat(lifecycle.fire(StandardDunningCaseEvent.RESUME).isAllowed()).isFalse();
    assertThat(lifecycle.fire(StandardDunningCaseEvent.WRITE_OFF).isAllowed()).isTrue();
    assertThat(lifecycle.currentState()).isEqualTo(StandardDunningCaseStatus.WRITTEN_OFF);
  }

  @Test
  void completedAndWrittenOffAreTerminal() {
    StateMachine<DunningCaseStatus, DunningCaseEvent> lifecycle = DunningCaseLifecycles.created();
    lifecycle.fire(StandardDunningCaseEvent.COMPLETE);

    assertThat(lifecycle.currentState()).isEqualTo(StandardDunningCaseStatus.COMPLETED);
    assertThat(lifecycle.fire(StandardDunningCaseEvent.ACTIVATE).isAllowed()).isFalse();
  }
}
