package io.genfin.reconciliation.validation;

import java.time.Instant;

/**
 * Everything a {@link ValidationRule} may need beyond the {@code Reconciliation} itself. Only
 * {@code asOf} is mandatory; every other field is optional and a rule that does not need it simply
 * reports no issue.
 */
public record ValidationContext(Instant asOf) {

  public static ValidationContext at(Instant asOf) {
    return new ValidationContext(asOf);
  }
}
