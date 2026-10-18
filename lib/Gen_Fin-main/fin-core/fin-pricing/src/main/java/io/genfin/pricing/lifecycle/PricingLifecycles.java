package io.genfin.pricing.lifecycle;

import io.genfin.api.statemachine.StateMachine;
import io.genfin.pricing.internal.lifecycle.DefaultPricingLifecycleProvider;
import io.genfin.pricing.port.lifecycle.PricingLifecycleProvider;

/** Access point for the standard {@link PricingLifecycleProvider}. */
public final class PricingLifecycles {

  private static final PricingLifecycleProvider STANDARD = new DefaultPricingLifecycleProvider();

  private PricingLifecycles() {}

  public static PricingLifecycleProvider standard() {
    return STANDARD;
  }

  public static StateMachine<PricingStatus, PricingEvent> created() {
    return STANDARD.create(PricingRequestStatus.CREATED);
  }
}
