package io.genfin.reconciliation.comparison;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import java.time.Duration;

/**
 * Tunables a {@code ComparisonStrategy} reads instead of hard-coding thresholds: how much amount
 * variance and timing drift still count as {@link DifferenceSeverity#MINOR} rather than {@link
 * DifferenceSeverity#MAJOR}. Deliberately separate from {@code MatchingContext} — Comparison is a
 * lower-level building block that Matching depends on, not the reverse.
 */
public record ComparisonContext(Money amountTolerance, Duration dateTolerance)
    implements ValueObject {

  private static final Duration DEFAULT_DATE_TOLERANCE = Duration.ofDays(1);

  public ComparisonContext {
    Validate.notNull(dateTolerance, "dateTolerance must not be null.");
    Validate.required(!dateTolerance.isNegative(), "dateTolerance must not be negative.");
    if (amountTolerance != null) {
      Validate.required(!amountTolerance.isNegative(), "amountTolerance must not be negative.");
    }
  }

  public static ComparisonContext strict() {
    return new ComparisonContext(null, DEFAULT_DATE_TOLERANCE);
  }

  public static ComparisonContext of(Money amountTolerance, Duration dateTolerance) {
    return new ComparisonContext(amountTolerance, dateTolerance);
  }
}
