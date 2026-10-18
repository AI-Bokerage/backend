package io.genfin.reconciliation.internal.matching;

import io.genfin.money.money.Money;
import io.genfin.reconciliation.matching.MatchCandidate;
import io.genfin.reconciliation.matching.MatchResult;
import io.genfin.reconciliation.matching.MatchingContext;
import io.genfin.reconciliation.port.matching.MatchingStrategy;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Matches when both candidates share a currency and their amount difference is within {@link
 * MatchingContext#tolerance()} — e.g. a settlement short by a rounded fee. Confidence degrades
 * linearly from 1.0 at zero variance to 0.0 at the tolerance boundary.
 */
public final class ToleranceMatch implements MatchingStrategy {

  @Override
  public MatchResult match(MatchCandidate left, MatchCandidate right, MatchingContext context) {
    Money tolerance = context.amountTolerance();
    if (tolerance == null || !left.amount().currency().equals(right.amount().currency())) {
      return MatchResult.notMatched(left.id(), name());
    }
    Money diff = left.amount().subtract(right.amount()).abs();
    if (diff.isZero()) {
      return MatchResult.matched(left.id(), right.id(), name());
    }
    if (diff.compareTo(tolerance) <= 0) {
      double confidence = confidenceFor(diff, tolerance);
      return MatchResult.partiallyMatched(
          left.id(), right.id(), confidence, diff, name(), "Amount within tolerance " + tolerance);
    }
    return MatchResult.notMatched(left.id(), name());
  }

  private double confidenceFor(Money diff, Money tolerance) {
    if (tolerance.isZero()) {
      return 0.0;
    }
    BigDecimal ratio = diff.amount().divide(tolerance.amount(), 6, RoundingMode.HALF_UP);
    double confidence = 1.0 - ratio.doubleValue();
    return Math.max(0.0, Math.min(1.0, confidence));
  }

  private String name() {
    return "ToleranceMatch";
  }
}
