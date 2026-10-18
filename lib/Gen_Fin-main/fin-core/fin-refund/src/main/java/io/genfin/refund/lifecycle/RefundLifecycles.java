package io.genfin.refund.lifecycle;

import io.genfin.api.statemachine.StateMachine;
import io.genfin.refund.internal.lifecycle.DefaultRefundLifecycleProvider;
import io.genfin.refund.port.lifecycle.RefundLifecycleProvider;

public final class RefundLifecycles {

  private static final RefundLifecycleProvider STANDARD = new DefaultRefundLifecycleProvider();

  private RefundLifecycles() {}

  public static RefundLifecycleProvider standard() {
    return STANDARD;
  }

  public static StateMachine<RefundStatus, RefundEvent> requested() {
    return STANDARD.create(StandardRefundStatus.REQUESTED);
  }
}
