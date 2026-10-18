package io.genfin.reconciliation.matching;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import java.time.Duration;
import java.util.Optional;

/**
 * Tunables a {@code MatchingStrategy} reads instead of hard-coding thresholds: how much amount
 * variance and timing drift are still considered a match, and how similar reference text must be
 * for a fuzzy match.
 */
public record MatchingContext(Money amountTolerance, Duration dateTolerance, double fuzzyThreshold)
    implements ValueObject {

  private static final Duration DEFAULT_DATE_TOLERANCE = Duration.ofDays(1);
  private static final double DEFAULT_FUZZY_THRESHOLD = 0.85;

  public MatchingContext {
    Validate.notNull(dateTolerance, "dateTolerance must not be null.");
    Validate.required(!dateTolerance.isNegative(), "dateTolerance must not be negative.");
    Validate.required(
        fuzzyThreshold >= 0.0 && fuzzyThreshold <= 1.0,
        "fuzzyThreshold must be between 0.0 and 1.0.");
    if (amountTolerance != null) {
      Validate.required(!amountTolerance.isNegative(), "amountTolerance must not be negative.");
    }
  }

  public static MatchingContext of(Money amountTolerance) {
    return new MatchingContext(amountTolerance, DEFAULT_DATE_TOLERANCE, DEFAULT_FUZZY_THRESHOLD);
  }

  public static MatchingContext strict() {
    return new MatchingContext(null, DEFAULT_DATE_TOLERANCE, DEFAULT_FUZZY_THRESHOLD);
  }

  public Optional<Money> tolerance() {
    return Optional.ofNullable(amountTolerance);
  }
}
