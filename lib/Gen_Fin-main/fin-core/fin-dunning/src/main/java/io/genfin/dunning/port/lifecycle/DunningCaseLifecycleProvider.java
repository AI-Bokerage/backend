package io.genfin.dunning.port.lifecycle;

import io.genfin.api.port.spi.Extension;
import io.genfin.api.statemachine.StateMachine;
import io.genfin.dunning.lifecycle.DunningCaseEvent;
import io.genfin.dunning.lifecycle.DunningCaseStatus;

/** SPI for building the {@link StateMachine} that drives a dunning case's lifecycle. */
public interface DunningCaseLifecycleProvider extends Extension {

  StateMachine<DunningCaseStatus, DunningCaseEvent> create(DunningCaseStatus initialState);
}
