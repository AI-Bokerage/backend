package io.genfin.reconciliation.internal.matching;

import io.genfin.reconciliation.matching.MatchCandidate;
import io.genfin.reconciliation.matching.MatchResult;
import io.genfin.reconciliation.matching.MatchingContext;
import io.genfin.reconciliation.port.matching.MatchingStrategy;

/** Matches when both candidates carry the exact same {@code Money} amount. */
public final class AmountMatch implements MatchingStrategy {

  @Override
  public MatchResult match(MatchCandidate left, MatchCandidate right, MatchingContext context) {
    if (left.amount().equals(right.amount())) {
      return MatchResult.matched(left.id(), right.id(), name());
    }
    return MatchResult.notMatched(left.id(), name());
  }

  private String name() {
    return "AmountMatch";
  }
}
