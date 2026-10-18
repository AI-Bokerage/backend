package io.genfin.pricing.lifecycle;

/** The standard {@link io.genfin.pricing.pricing.PricingRequest} lifecycle events. */
public enum PricingRequestEvent implements PricingEvent {
  VALIDATE,
  CALCULATE,
  COMPLETE,
  REJECT,
  EXPIRE;

  @Override
  public String code() {
    return name();
  }
}
