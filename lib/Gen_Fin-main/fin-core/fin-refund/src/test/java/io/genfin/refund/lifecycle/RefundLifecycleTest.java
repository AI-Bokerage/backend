package io.genfin.refund.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.api.statemachine.StateMachine;
import org.junit.jupiter.api.Test;

class RefundLifecycleTest {

  @Test
  void standardLifecycleFollowsSubmitApproveProcessCompletePath() {
    StateMachine<RefundStatus, RefundEvent> lifecycle = RefundLifecycles.requested();

    assertThat(lifecycle.fire(StandardRefundEvent.SUBMIT_FOR_APPROVAL).isAllowed()).isTrue();
    assertThat(lifecycle.fire(StandardRefundEvent.APPROVE).isAllowed()).isTrue();
    assertThat(lifecycle.fire(StandardRefundEvent.PROCESS).isAllowed()).isTrue();
    assertThat(lifecycle.fire(StandardRefundEvent.COMPLETE).isAllowed()).isTrue();
    assertThat(lifecycle.currentState()).isEqualTo(StandardRefundStatus.COMPLETED);
  }

  @Test
  void pendingApprovalCanBeRejected() {
    StateMachine<RefundStatus, RefundEvent> lifecycle = RefundLifecycles.requested();
    lifecycle.fire(StandardRefundEvent.SUBMIT_FOR_APPROVAL);

    assertThat(lifecycle.fire(StandardRefundEvent.REJECT).isAllowed()).isTrue();
    assertThat(lifecycle.currentState()).isEqualTo(StandardRefundStatus.REJECTED);
  }

  @Test
  void requestedCannotJumpDirectlyToProcessing() {
    StateMachine<RefundStatus, RefundEvent> lifecycle = RefundLifecycles.requested();

    assertThat(lifecycle.fire(StandardRefundEvent.PROCESS).isAllowed()).isFalse();
    assertThat(lifecycle.currentState()).isEqualTo(StandardRefundStatus.REQUESTED);
  }

  @Test
  void processingCanPartiallyCompleteThenLaterFullyComplete() {
    StateMachine<RefundStatus, RefundEvent> lifecycle = RefundLifecycles.requested();
    lifecycle.fire(StandardRefundEvent.SUBMIT_FOR_APPROVAL);
    lifecycle.fire(StandardRefundEvent.APPROVE);
    lifecycle.fire(StandardRefundEvent.PROCESS);

    assertThat(lifecycle.fire(StandardRefundEvent.PARTIALLY_COMPLETE).isAllowed()).isTrue();
    assertThat(lifecycle.currentState()).isEqualTo(StandardRefundStatus.PARTIALLY_PROCESSED);

    assertThat(lifecycle.fire(StandardRefundEvent.COMPLETE).isAllowed()).isTrue();
    assertThat(lifecycle.currentState()).isEqualTo(StandardRefundStatus.COMPLETED);
  }

  @Test
  void aFailedProcessingAttemptCanBeRetried() {
    StateMachine<RefundStatus, RefundEvent> lifecycle = RefundLifecycles.requested();
    lifecycle.fire(StandardRefundEvent.SUBMIT_FOR_APPROVAL);
    lifecycle.fire(StandardRefundEvent.APPROVE);
    lifecycle.fire(StandardRefundEvent.PROCESS);
    lifecycle.fire(StandardRefundEvent.FAIL);

    assertThat(lifecycle.currentState()).isEqualTo(StandardRefundStatus.FAILED);
    assertThat(lifecycle.fire(StandardRefundEvent.PROCESS).isAllowed()).isTrue();
    assertThat(lifecycle.currentState()).isEqualTo(StandardRefundStatus.PROCESSING);
  }

  @Test
  void completedRefundCanBeDisputedThenReversed() {
    StateMachine<RefundStatus, RefundEvent> lifecycle = RefundLifecycles.requested();
    lifecycle.fire(StandardRefundEvent.SUBMIT_FOR_APPROVAL);
    lifecycle.fire(StandardRefundEvent.APPROVE);
    lifecycle.fire(StandardRefundEvent.PROCESS);
    lifecycle.fire(StandardRefundEvent.COMPLETE);

    assertThat(lifecycle.fire(StandardRefundEvent.DISPUTE).isAllowed()).isTrue();
    assertThat(lifecycle.fire(StandardRefundEvent.REVERSE).isAllowed()).isTrue();
    assertThat(lifecycle.currentState()).isEqualTo(StandardRefundStatus.REVERSED);
  }

  @Test
  void requestedOrPendingApprovalOrApprovedCanBeCancelled() {
    assertThat(RefundLifecycles.requested().fire(StandardRefundEvent.CANCEL).isAllowed()).isTrue();

    StateMachine<RefundStatus, RefundEvent> pendingApproval = RefundLifecycles.requested();
    pendingApproval.fire(StandardRefundEvent.SUBMIT_FOR_APPROVAL);
    assertThat(pendingApproval.fire(StandardRefundEvent.CANCEL).isAllowed()).isTrue();

    StateMachine<RefundStatus, RefundEvent> approved = RefundLifecycles.requested();
    approved.fire(StandardRefundEvent.SUBMIT_FOR_APPROVAL);
    approved.fire(StandardRefundEvent.APPROVE);
    assertThat(approved.fire(StandardRefundEvent.CANCEL).isAllowed()).isTrue();
  }

  @Test
  void completedRefundCannotBeCancelled() {
    StateMachine<RefundStatus, RefundEvent> lifecycle = RefundLifecycles.requested();
    lifecycle.fire(StandardRefundEvent.SUBMIT_FOR_APPROVAL);
    lifecycle.fire(StandardRefundEvent.APPROVE);
    lifecycle.fire(StandardRefundEvent.PROCESS);
    lifecycle.fire(StandardRefundEvent.COMPLETE);

    assertThat(lifecycle.fire(StandardRefundEvent.CANCEL).isAllowed()).isFalse();
  }
}
