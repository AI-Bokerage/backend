package io.genfin.pricing.quote;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;

/**
 * An optimistic-concurrency version marker: bumped each time a {@link Quote} is revised, so
 * consumers can tell one issued offer apart from a later revision of the same commercial offer.
 * Mirrors {@code io.genfin.pricing.pricing.PricingVersion}.
 */
public record QuoteVersion(int number) implements ValueObject {

  public QuoteVersion {
    Validate.nonNegative(number, "number must not be negative.");
  }

  public static QuoteVersion initial() {
    return new QuoteVersion(1);
  }

  public QuoteVersion next() {
    return new QuoteVersion(number + 1);
  }
}
