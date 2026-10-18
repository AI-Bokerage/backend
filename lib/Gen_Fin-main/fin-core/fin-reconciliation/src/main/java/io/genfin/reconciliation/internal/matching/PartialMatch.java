package io.genfin.reconciliation.internal.matching;

import io.genfin.money.money.Money;
import io.genfin.reconciliation.matching.MatchCandidate;
import io.genfin.reconciliation.matching.MatchResult;
import io.genfin.reconciliation.matching.MatchingContext;
import io.genfin.reconciliation.port.matching.MatchingStrategy;
import java.math.RoundingMode;

/**
 * Matches when the same reference is settled for strictly less than the expected amount — e.g. a
 * partial capture or an installment payment. Confidence is the fraction of the amount actually
 * covered.
 */
public final class PartialMatch implements MatchingStrategy {

  @Override
  public MatchResult match(MatchCandidate left, MatchCandidate right, MatchingContext context) {
    if (!left.reference().equals(right.reference())
        || !left.amount().currency().equals(right.amount().currency())
        || left.amount().isZero()
        || right.amount().compareTo(left.amount()) >= 0) {
      return MatchResult.notMatched(left.id(), name());
    }
    Money shortfall = left.amount().subtract(right.amount());
    double confidence =
        right
            .amount()
            .amount()
            .divide(left.amount().amount(), 6, RoundingMode.HALF_UP)
            .doubleValue();
    return MatchResult.partiallyMatched(
        left.id(),
        right.id(),
        Math.max(0.0, Math.min(1.0, confidence)),
        shortfall,
        name(),
        "Covered " + right.amount() + " of " + left.amount());
  }

  private String name() {
    return "PartialMatch";
  }
}
