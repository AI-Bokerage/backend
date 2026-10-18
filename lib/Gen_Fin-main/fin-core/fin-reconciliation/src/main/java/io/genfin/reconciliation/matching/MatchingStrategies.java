package io.genfin.reconciliation.matching;

import io.genfin.reconciliation.internal.matching.AmountMatch;
import io.genfin.reconciliation.internal.matching.CompositeMatch;
import io.genfin.reconciliation.internal.matching.DateMatch;
import io.genfin.reconciliation.internal.matching.ExactMatch;
import io.genfin.reconciliation.internal.matching.FuzzyMatch;
import io.genfin.reconciliation.internal.matching.PartialMatch;
import io.genfin.reconciliation.internal.matching.ReferenceMatch;
import io.genfin.reconciliation.internal.matching.ToleranceMatch;
import io.genfin.reconciliation.port.matching.MatchingStrategy;
import java.util.List;

/** Factory for the default {@link MatchingStrategy} implementations. */
public final class MatchingStrategies {

  private MatchingStrategies() {}

  public static MatchingStrategy exact() {
    return new ExactMatch();
  }

  public static MatchingStrategy amount() {
    return new AmountMatch();
  }

  public static MatchingStrategy date() {
    return new DateMatch();
  }

  public static MatchingStrategy reference() {
    return new ReferenceMatch();
  }

  public static MatchingStrategy tolerance() {
    return new ToleranceMatch();
  }

  public static MatchingStrategy partial() {
    return new PartialMatch();
  }

  public static MatchingStrategy fuzzy() {
    return new FuzzyMatch();
  }

  /** Matches only when every one of {@code strategies} agrees (weakest confidence wins). */
  public static MatchingStrategy allOf(List<MatchingStrategy> strategies) {
    return new CompositeMatch(strategies, true);
  }

  /** Matches on the first of {@code strategies} (in order) that agrees. */
  public static MatchingStrategy anyOf(List<MatchingStrategy> strategies) {
    return new CompositeMatch(strategies, false);
  }

  /** The standard fallback chain: exact, then tolerance, then reference, then fuzzy. */
  public static List<MatchingStrategy> standardChain() {
    return List.of(exact(), tolerance(), reference(), fuzzy(), partial());
  }
}
