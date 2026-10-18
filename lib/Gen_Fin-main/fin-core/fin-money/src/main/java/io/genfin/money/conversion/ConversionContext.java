package io.genfin.money.conversion;

import io.genfin.money.port.rounding.RoundingStrategy;
import java.time.Instant;

/** Point-in-time and rounding context for a single conversion call. */
public record ConversionContext(Instant asOf, RoundingStrategy roundingOverride) {

  public static ConversionContext at(Instant asOf) {
    return new ConversionContext(asOf, null);
  }
}
