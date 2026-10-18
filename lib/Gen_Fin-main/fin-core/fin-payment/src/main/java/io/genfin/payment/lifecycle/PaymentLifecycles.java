package io.genfin.payment.lifecycle;

import io.genfin.api.statemachine.StateMachine;
import io.genfin.payment.internal.lifecycle.DefaultLifecycleProvider;
import io.genfin.payment.port.lifecycle.LifecycleProvider;

public final class PaymentLifecycles {

  private static final LifecycleProvider STANDARD = new DefaultLifecycleProvider();

  private PaymentLifecycles() {}

  public static LifecycleProvider standard() {
    return STANDARD;
  }

  public static StateMachine<PaymentState, PaymentEvent> created() {
    return STANDARD.create(StandardPaymentState.CREATED);
  }
}
