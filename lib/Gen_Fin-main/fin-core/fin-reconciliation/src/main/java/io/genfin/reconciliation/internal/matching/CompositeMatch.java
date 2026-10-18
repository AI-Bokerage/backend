package io.genfin.reconciliation.internal.matching;

import io.genfin.api.validation.Validate;
import io.genfin.reconciliation.matching.MatchCandidate;
import io.genfin.reconciliation.matching.MatchOutcome;
import io.genfin.reconciliation.matching.MatchResult;
import io.genfin.reconciliation.matching.MatchingContext;
import io.genfin.reconciliation.port.matching.MatchingStrategy;
import java.util.List;

/**
 * Combines several strategies without any {@code instanceof}/{@code switch}: in {@code requireAll}
 * mode every strategy must produce at least {@link MatchOutcome#PARTIALLY_MATCHED} and the weakest
 * confidence wins; otherwise the first strategy that matches (in list order) wins.
 */
public final class CompositeMatch implements MatchingStrategy {

  private static final double FULL_CONFIDENCE = 1.0;

  private final List<MatchingStrategy> strategies;
  private final boolean requireAll;

  public CompositeMatch(List<MatchingStrategy> strategies, boolean requireAll) {
    Validate.notNull(strategies, "strategies must not be null.");
    Validate.required(!strategies.isEmpty(), "strategies must not be empty.");
    this.strategies = List.copyOf(strategies);
    this.requireAll = requireAll;
  }

  @Override
  public MatchResult match(MatchCandidate left, MatchCandidate right, MatchingContext context) {
    return requireAll ? matchAll(left, right, context) : matchAny(left, right, context);
  }

  private MatchResult matchAny(MatchCandidate left, MatchCandidate right, MatchingContext context) {
    for (MatchingStrategy strategy : strategies) {
      MatchResult result = strategy.match(left, right, context);
      if (result.isMatch()) {
        return result;
      }
    }
    return MatchResult.notMatched(left.id(), name());
  }

  private MatchResult matchAll(MatchCandidate left, MatchCandidate right, MatchingContext context) {
    double weakest = FULL_CONFIDENCE;
    for (MatchingStrategy strategy : strategies) {
      MatchResult result = strategy.match(left, right, context);
      if (!result.isMatch()) {
        return MatchResult.notMatched(left.id(), name());
      }
      weakest = Math.min(weakest, result.confidence());
    }
    if (weakest >= FULL_CONFIDENCE) {
      return MatchResult.matched(left.id(), right.id(), name());
    }
    return MatchResult.partiallyMatched(
        left.id(), right.id(), weakest, null, name(), "All sub-strategies agreed, weakest wins.");
  }

  private String name() {
    return "CompositeMatch";
  }
}
