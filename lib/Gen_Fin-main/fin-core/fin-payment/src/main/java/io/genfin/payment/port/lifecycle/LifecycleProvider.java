package io.genfin.payment.port.lifecycle;

import io.genfin.api.port.spi.Extension;
import io.genfin.api.statemachine.StateMachine;
import io.genfin.payment.lifecycle.PaymentEvent;
import io.genfin.payment.lifecycle.PaymentState;

public interface LifecycleProvider extends Extension {

  StateMachine<PaymentState, PaymentEvent> create(PaymentState initialState);
}
