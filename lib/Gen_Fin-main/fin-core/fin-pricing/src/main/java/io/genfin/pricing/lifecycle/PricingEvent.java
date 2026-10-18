package io.genfin.pricing.lifecycle;

/** An event that drives a {@link io.genfin.pricing.pricing.PricingRequest} lifecycle transition. */
public interface PricingEvent {

  String code();
}
