package io.genfin.payment.internal.lifecycle;

import static io.genfin.payment.lifecycle.StandardPaymentEvent.AUTHORIZE;
import static io.genfin.payment.lifecycle.StandardPaymentEvent.CANCEL;
import static io.genfin.payment.lifecycle.StandardPaymentEvent.CAPTURE;
import static io.genfin.payment.lifecycle.StandardPaymentEvent.DISPUTE;
import static io.genfin.payment.lifecycle.StandardPaymentEvent.EXPIRE;
import static io.genfin.payment.lifecycle.StandardPaymentEvent.FAIL;
import static io.genfin.payment.lifecycle.StandardPaymentEvent.MARK_CHARGEBACK;
import static io.genfin.payment.lifecycle.StandardPaymentEvent.PARTIALLY_AUTHORIZE;
import static io.genfin.payment.lifecycle.StandardPaymentEvent.PARTIALLY_CAPTURE;
import static io.genfin.payment.lifecycle.StandardPaymentEvent.PARTIALLY_REFUND;
import static io.genfin.payment.lifecycle.StandardPaymentEvent.REFUND;
import static io.genfin.payment.lifecycle.StandardPaymentEvent.SETTLE;
import static io.genfin.payment.lifecycle.StandardPaymentEvent.SUBMIT;
import static io.genfin.payment.lifecycle.StandardPaymentState.AUTHORIZED;
import static io.genfin.payment.lifecycle.StandardPaymentState.CANCELLED;
import static io.genfin.payment.lifecycle.StandardPaymentState.CAPTURED;
import static io.genfin.payment.lifecycle.StandardPaymentState.CHARGEBACK;
import static io.genfin.payment.lifecycle.StandardPaymentState.CREATED;
import static io.genfin.payment.lifecycle.StandardPaymentState.DISPUTED;
import static io.genfin.payment.lifecycle.StandardPaymentState.EXPIRED;
import static io.genfin.payment.lifecycle.StandardPaymentState.FAILED;
import static io.genfin.payment.lifecycle.StandardPaymentState.PARTIALLY_AUTHORIZED;
import static io.genfin.payment.lifecycle.StandardPaymentState.PARTIALLY_CAPTURED;
import static io.genfin.payment.lifecycle.StandardPaymentState.PARTIALLY_REFUNDED;
import static io.genfin.payment.lifecycle.StandardPaymentState.PENDING;
import static io.genfin.payment.lifecycle.StandardPaymentState.REFUNDED;
import static io.genfin.payment.lifecycle.StandardPaymentState.SETTLED;

import io.genfin.api.statemachine.StateMachine;
import io.genfin.api.statemachine.StateMachines;
import io.genfin.api.statemachine.Transition;
import io.genfin.payment.lifecycle.PaymentEvent;
import io.genfin.payment.lifecycle.PaymentState;
import io.genfin.payment.port.lifecycle.LifecycleProvider;
import java.util.List;

/**
 * The standard payment lifecycle: Created → Pending → (Partially) Authorized → (Partially) Captured
 * → Settled, with refund/dispute side paths.
 */
public final class DefaultLifecycleProvider implements LifecycleProvider {

  @Override
  public StateMachine<PaymentState, PaymentEvent> create(PaymentState initialState) {
    return StateMachines.of(initialState, transitions());
  }

  private static List<Transition<PaymentState, PaymentEvent>> transitions() {
    return List.of(
        new Transition<>(CREATED, SUBMIT, PENDING),
        new Transition<>(CREATED, CANCEL, CANCELLED),
        new Transition<>(PENDING, AUTHORIZE, AUTHORIZED),
        new Transition<>(PENDING, PARTIALLY_AUTHORIZE, PARTIALLY_AUTHORIZED),
        new Transition<>(PENDING, FAIL, FAILED),
        new Transition<>(PENDING, CANCEL, CANCELLED),
        new Transition<>(PENDING, EXPIRE, EXPIRED),
        new Transition<>(PARTIALLY_AUTHORIZED, AUTHORIZE, AUTHORIZED),
        new Transition<>(PARTIALLY_AUTHORIZED, CAPTURE, PARTIALLY_CAPTURED),
        new Transition<>(PARTIALLY_AUTHORIZED, FAIL, FAILED),
        new Transition<>(PARTIALLY_AUTHORIZED, CANCEL, CANCELLED),
        new Transition<>(PARTIALLY_AUTHORIZED, EXPIRE, EXPIRED),
        new Transition<>(AUTHORIZED, CAPTURE, CAPTURED),
        new Transition<>(AUTHORIZED, PARTIALLY_CAPTURE, PARTIALLY_CAPTURED),
        new Transition<>(AUTHORIZED, CANCEL, CANCELLED),
        new Transition<>(AUTHORIZED, EXPIRE, EXPIRED),
        new Transition<>(PARTIALLY_CAPTURED, CAPTURE, CAPTURED),
        new Transition<>(PARTIALLY_CAPTURED, SETTLE, SETTLED),
        new Transition<>(PARTIALLY_CAPTURED, REFUND, REFUNDED),
        new Transition<>(PARTIALLY_CAPTURED, PARTIALLY_REFUND, PARTIALLY_REFUNDED),
        new Transition<>(CAPTURED, SETTLE, SETTLED),
        new Transition<>(CAPTURED, REFUND, REFUNDED),
        new Transition<>(CAPTURED, PARTIALLY_REFUND, PARTIALLY_REFUNDED),
        new Transition<>(CAPTURED, DISPUTE, DISPUTED),
        new Transition<>(SETTLED, REFUND, REFUNDED),
        new Transition<>(SETTLED, PARTIALLY_REFUND, PARTIALLY_REFUNDED),
        new Transition<>(SETTLED, DISPUTE, DISPUTED),
        new Transition<>(PARTIALLY_REFUNDED, REFUND, REFUNDED),
        new Transition<>(DISPUTED, MARK_CHARGEBACK, CHARGEBACK),
        new Transition<>(DISPUTED, SETTLE, SETTLED));
  }
}
