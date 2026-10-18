package io.genfin.refund.internal.lifecycle;

import static io.genfin.refund.lifecycle.StandardRefundEvent.APPROVE;
import static io.genfin.refund.lifecycle.StandardRefundEvent.CANCEL;
import static io.genfin.refund.lifecycle.StandardRefundEvent.COMPLETE;
import static io.genfin.refund.lifecycle.StandardRefundEvent.DISPUTE;
import static io.genfin.refund.lifecycle.StandardRefundEvent.FAIL;
import static io.genfin.refund.lifecycle.StandardRefundEvent.PARTIALLY_COMPLETE;
import static io.genfin.refund.lifecycle.StandardRefundEvent.PROCESS;
import static io.genfin.refund.lifecycle.StandardRefundEvent.REJECT;
import static io.genfin.refund.lifecycle.StandardRefundEvent.REVERSE;
import static io.genfin.refund.lifecycle.StandardRefundEvent.SUBMIT_FOR_APPROVAL;
import static io.genfin.refund.lifecycle.StandardRefundStatus.APPROVED;
import static io.genfin.refund.lifecycle.StandardRefundStatus.CANCELLED;
import static io.genfin.refund.lifecycle.StandardRefundStatus.COMPLETED;
import static io.genfin.refund.lifecycle.StandardRefundStatus.DISPUTED;
import static io.genfin.refund.lifecycle.StandardRefundStatus.FAILED;
import static io.genfin.refund.lifecycle.StandardRefundStatus.PARTIALLY_PROCESSED;
import static io.genfin.refund.lifecycle.StandardRefundStatus.PENDING_APPROVAL;
import static io.genfin.refund.lifecycle.StandardRefundStatus.PROCESSING;
import static io.genfin.refund.lifecycle.StandardRefundStatus.REJECTED;
import static io.genfin.refund.lifecycle.StandardRefundStatus.REQUESTED;
import static io.genfin.refund.lifecycle.StandardRefundStatus.REVERSED;

import io.genfin.api.statemachine.StateMachine;
import io.genfin.api.statemachine.StateMachines;
import io.genfin.api.statemachine.Transition;
import io.genfin.refund.lifecycle.RefundEvent;
import io.genfin.refund.lifecycle.RefundStatus;
import io.genfin.refund.port.lifecycle.RefundLifecycleProvider;
import java.util.List;

/**
 * The standard refund lifecycle: Requested → PendingApproval → Approved → Processing →
 * (Partially)Completed, with reject/cancel/fail/reverse/dispute side paths.
 */
public final class DefaultRefundLifecycleProvider implements RefundLifecycleProvider {

  @Override
  public StateMachine<RefundStatus, RefundEvent> create(RefundStatus initialState) {
    return StateMachines.of(initialState, transitions());
  }

  private static List<Transition<RefundStatus, RefundEvent>> transitions() {
    return List.of(
        new Transition<>(REQUESTED, SUBMIT_FOR_APPROVAL, PENDING_APPROVAL),
        new Transition<>(REQUESTED, CANCEL, CANCELLED),
        new Transition<>(PENDING_APPROVAL, APPROVE, APPROVED),
        new Transition<>(PENDING_APPROVAL, REJECT, REJECTED),
        new Transition<>(PENDING_APPROVAL, CANCEL, CANCELLED),
        new Transition<>(APPROVED, PROCESS, PROCESSING),
        new Transition<>(APPROVED, CANCEL, CANCELLED),
        new Transition<>(PROCESSING, COMPLETE, COMPLETED),
        new Transition<>(PROCESSING, PARTIALLY_COMPLETE, PARTIALLY_PROCESSED),
        new Transition<>(PROCESSING, FAIL, FAILED),
        new Transition<>(PARTIALLY_PROCESSED, COMPLETE, COMPLETED),
        new Transition<>(PARTIALLY_PROCESSED, FAIL, FAILED),
        new Transition<>(PARTIALLY_PROCESSED, REVERSE, REVERSED),
        new Transition<>(FAILED, PROCESS, PROCESSING),
        new Transition<>(COMPLETED, REVERSE, REVERSED),
        new Transition<>(COMPLETED, DISPUTE, DISPUTED),
        new Transition<>(DISPUTED, REVERSE, REVERSED));
  }
}
