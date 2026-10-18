package io.genfin.pricing.port.lifecycle;

import io.genfin.api.port.spi.Extension;
import io.genfin.api.statemachine.StateMachine;
import io.genfin.pricing.lifecycle.PricingEvent;
import io.genfin.pricing.lifecycle.PricingStatus;

/** SPI for building the {@link StateMachine} that drives a {@code PricingRequest}'s lifecycle. */
public interface PricingLifecycleProvider extends Extension {

  StateMachine<PricingStatus, PricingEvent> create(PricingStatus initialState);
}
