package io.genfin.pricing.price;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import java.time.Instant;

/**
 * An immutable point-in-time capture of a {@link Price}, kept when it is carried forward across a
 * repricing (e.g. quoted into a {@code Quote}, or recorded in {@code
 * io.genfin.pricing.pricing.PricingHistory}) so a later {@link Price} for the same line can be
 * compared against what was shown before.
 */
public record PriceSnapshot(Price price, Instant capturedAt) implements ValueObject {

  public PriceSnapshot {
    Validate.notNull(price, "price must not be null.");
    Validate.notNull(capturedAt, "capturedAt must not be null.");
  }

  public static PriceSnapshot capture(Price price) {
    return new PriceSnapshot(price, Instant.now());
  }
}
