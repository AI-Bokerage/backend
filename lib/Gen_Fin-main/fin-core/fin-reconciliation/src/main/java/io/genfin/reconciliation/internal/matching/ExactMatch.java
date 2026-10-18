package io.genfin.reconciliation.internal.matching;

import io.genfin.reconciliation.matching.MatchCandidate;
import io.genfin.reconciliation.matching.MatchResult;
import io.genfin.reconciliation.matching.MatchingContext;
import io.genfin.reconciliation.port.matching.MatchingStrategy;

/**
 * The strictest strategy: matches only when both amount and reference are identical. Equivalent to
 * an all-of {@code CompositeMatch} of {@link AmountMatch} and {@link ReferenceMatch}, implemented
 * directly to keep the common case cheap and dependency-free.
 */
public final class ExactMatch implements MatchingStrategy {

  @Override
  public MatchResult match(MatchCandidate left, MatchCandidate right, MatchingContext context) {
    if (left.amount().equals(right.amount()) && left.reference().equals(right.reference())) {
      return MatchResult.matched(left.id(), right.id(), name());
    }
    return MatchResult.notMatched(left.id(), name());
  }

  private String name() {
    return "ExactMatch";
  }
}
