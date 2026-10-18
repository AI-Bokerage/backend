package io.genfin.pricing.internal.lifecycle;

import static io.genfin.pricing.lifecycle.PricingRequestEvent.CALCULATE;
import static io.genfin.pricing.lifecycle.PricingRequestEvent.COMPLETE;
import static io.genfin.pricing.lifecycle.PricingRequestEvent.EXPIRE;
import static io.genfin.pricing.lifecycle.PricingRequestEvent.REJECT;
import static io.genfin.pricing.lifecycle.PricingRequestEvent.VALIDATE;
import static io.genfin.pricing.lifecycle.PricingRequestStatus.CALCULATING;
import static io.genfin.pricing.lifecycle.PricingRequestStatus.CREATED;
import static io.genfin.pricing.lifecycle.PricingRequestStatus.EXPIRED;
import static io.genfin.pricing.lifecycle.PricingRequestStatus.PRICED;
import static io.genfin.pricing.lifecycle.PricingRequestStatus.REJECTED;
import static io.genfin.pricing.lifecycle.PricingRequestStatus.VALIDATING;

import io.genfin.api.statemachine.StateMachine;
import io.genfin.api.statemachine.StateMachines;
import io.genfin.api.statemachine.Transition;
import io.genfin.pricing.lifecycle.PricingEvent;
import io.genfin.pricing.lifecycle.PricingStatus;
import io.genfin.pricing.port.lifecycle.PricingLifecycleProvider;
import java.util.List;

/**
 * The standard pricing request lifecycle: Created → Validating → Calculating → Priced, with
 * Expired/Rejected as terminal failure states reachable from either in-flight state (Expired is
 * also reachable straight from Created, e.g. a quote that times out before validation starts).
 */
public final class DefaultPricingLifecycleProvider implements PricingLifecycleProvider {

  @Override
  public StateMachine<PricingStatus, PricingEvent> create(PricingStatus initialState) {
    return StateMachines.of(initialState, transitions());
  }

  private static List<Transition<PricingStatus, PricingEvent>> transitions() {
    return List.of(
        new Transition<>(CREATED, VALIDATE, VALIDATING),
        new Transition<>(CREATED, EXPIRE, EXPIRED),
        new Transition<>(VALIDATING, CALCULATE, CALCULATING),
        new Transition<>(VALIDATING, REJECT, REJECTED),
        new Transition<>(VALIDATING, EXPIRE, EXPIRED),
        new Transition<>(CALCULATING, COMPLETE, PRICED),
        new Transition<>(CALCULATING, REJECT, REJECTED),
        new Transition<>(CALCULATING, EXPIRE, EXPIRED));
  }
}
