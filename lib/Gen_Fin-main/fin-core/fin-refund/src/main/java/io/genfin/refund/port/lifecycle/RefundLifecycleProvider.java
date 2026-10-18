package io.genfin.refund.port.lifecycle;

import io.genfin.api.port.spi.Extension;
import io.genfin.api.statemachine.StateMachine;
import io.genfin.refund.lifecycle.RefundEvent;
import io.genfin.refund.lifecycle.RefundStatus;

public interface RefundLifecycleProvider extends Extension {

  StateMachine<RefundStatus, RefundEvent> create(RefundStatus initialState);
}
