package io.genfin.pricing.lifecycle;

/** The standard {@link io.genfin.pricing.pricing.PricingRequest} lifecycle states. */
public enum PricingRequestStatus implements PricingStatus {
  CREATED,
  VALIDATING,
  CALCULATING,
  PRICED,
  EXPIRED,
  REJECTED;

  @Override
  public String code() {
    return name();
  }
}
