package io.genfin.reconciliation.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.api.statemachine.StateMachine;
import org.junit.jupiter.api.Test;

class ReconciliationLifecycleTest {

  @Test
  void standardLifecycleFollowsCollectMatchAnalyzeReconcilePath() {
    StateMachine<ReconciliationStatus, ReconciliationEvent> lifecycle =
        ReconciliationLifecycles.created();

    assertThat(lifecycle.fire(StandardReconciliationEvent.COLLECT).isAllowed()).isTrue();
    assertThat(lifecycle.fire(StandardReconciliationEvent.MATCH).isAllowed()).isTrue();
    assertThat(lifecycle.fire(StandardReconciliationEvent.ANALYZE).isAllowed()).isTrue();
    assertThat(lifecycle.fire(StandardReconciliationEvent.RECONCILE).isAllowed()).isTrue();
    assertThat(lifecycle.currentState()).isEqualTo(StandardReconciliationStatus.RECONCILED);
  }

  @Test
  void createdCannotJumpDirectlyToAnalyzing() {
    StateMachine<ReconciliationStatus, ReconciliationEvent> lifecycle =
        ReconciliationLifecycles.created();

    assertThat(lifecycle.fire(StandardReconciliationEvent.ANALYZE).isAllowed()).isFalse();
    assertThat(lifecycle.currentState()).isEqualTo(StandardReconciliationStatus.CREATED);
  }

  @Test
  void analyzingCanPartiallyReconcileThenLaterFullyReconcile() {
    StateMachine<ReconciliationStatus, ReconciliationEvent> lifecycle =
        ReconciliationLifecycles.created();
    lifecycle.fire(StandardReconciliationEvent.COLLECT);
    lifecycle.fire(StandardReconciliationEvent.MATCH);
    lifecycle.fire(StandardReconciliationEvent.ANALYZE);

    assertThat(lifecycle.fire(StandardReconciliationEvent.PARTIALLY_RECONCILE).isAllowed())
        .isTrue();
    assertThat(lifecycle.currentState())
        .isEqualTo(StandardReconciliationStatus.PARTIALLY_RECONCILED);

    assertThat(lifecycle.fire(StandardReconciliationEvent.RECONCILE).isAllowed()).isTrue();
    assertThat(lifecycle.currentState()).isEqualTo(StandardReconciliationStatus.RECONCILED);
  }

  @Test
  void aFailedAnalysisCanBeRetriedByRestartingCollection() {
    StateMachine<ReconciliationStatus, ReconciliationEvent> lifecycle =
        ReconciliationLifecycles.created();
    lifecycle.fire(StandardReconciliationEvent.COLLECT);
    lifecycle.fire(StandardReconciliationEvent.MATCH);
    lifecycle.fire(StandardReconciliationEvent.ANALYZE);
    lifecycle.fire(StandardReconciliationEvent.FAIL);

    assertThat(lifecycle.currentState()).isEqualTo(StandardReconciliationStatus.FAILED);
    assertThat(lifecycle.fire(StandardReconciliationEvent.COLLECT).isAllowed()).isTrue();
    assertThat(lifecycle.currentState()).isEqualTo(StandardReconciliationStatus.COLLECTING);
  }

  @Test
  void createdCollectingMatchingAndAnalyzingCanBeCancelled() {
    assertThat(
            ReconciliationLifecycles.created().fire(StandardReconciliationEvent.CANCEL).isAllowed())
        .isTrue();

    StateMachine<ReconciliationStatus, ReconciliationEvent> analyzing =
        ReconciliationLifecycles.created();
    analyzing.fire(StandardReconciliationEvent.COLLECT);
    analyzing.fire(StandardReconciliationEvent.MATCH);
    analyzing.fire(StandardReconciliationEvent.ANALYZE);
    assertThat(analyzing.fire(StandardReconciliationEvent.CANCEL).isAllowed()).isTrue();
    assertThat(analyzing.currentState()).isEqualTo(StandardReconciliationStatus.CANCELLED);
  }

  @Test
  void reconciledCannotBeCancelled() {
    StateMachine<ReconciliationStatus, ReconciliationEvent> lifecycle =
        ReconciliationLifecycles.created();
    lifecycle.fire(StandardReconciliationEvent.COLLECT);
    lifecycle.fire(StandardReconciliationEvent.MATCH);
    lifecycle.fire(StandardReconciliationEvent.ANALYZE);
    lifecycle.fire(StandardReconciliationEvent.RECONCILE);

    assertThat(lifecycle.fire(StandardReconciliationEvent.CANCEL).isAllowed()).isFalse();
  }
}
