package io.genfin.invoice.validation;

import java.time.Instant;

public record ValidationContext(Instant asOf) {

  public static ValidationContext at(Instant asOf) {
    return new ValidationContext(asOf);
  }
}
