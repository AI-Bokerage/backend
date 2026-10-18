package io.genfin.reconciliation.internal.matching;

import io.genfin.reconciliation.matching.MatchCandidate;
import io.genfin.reconciliation.matching.MatchResult;
import io.genfin.reconciliation.matching.MatchingContext;
import io.genfin.reconciliation.port.matching.MatchingStrategy;

/** Matches when both candidates point at the exact same {@code Reference} (type and value). */
public final class ReferenceMatch implements MatchingStrategy {

  @Override
  public MatchResult match(MatchCandidate left, MatchCandidate right, MatchingContext context) {
    if (left.reference().equals(right.reference())) {
      return MatchResult.matched(left.id(), right.id(), name());
    }
    return MatchResult.notMatched(left.id(), name());
  }

  private String name() {
    return "ReferenceMatch";
  }
}
