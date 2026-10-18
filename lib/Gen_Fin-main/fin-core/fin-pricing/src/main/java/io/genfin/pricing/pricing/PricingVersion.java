package io.genfin.pricing.pricing;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;

/**
 * An optimistic-concurrency version marker: bumped each time a {@link PricingRequest} is
 * recalculated, so consumers can tell one {@link PricingResult} apart from a later repricing of the
 * same request.
 */
public record PricingVersion(int number) implements ValueObject {

  public PricingVersion {
    Validate.nonNegative(number, "number must not be negative.");
  }

  public static PricingVersion initial() {
    return new PricingVersion(1);
  }

  public PricingVersion next() {
    return new PricingVersion(number + 1);
  }
}
