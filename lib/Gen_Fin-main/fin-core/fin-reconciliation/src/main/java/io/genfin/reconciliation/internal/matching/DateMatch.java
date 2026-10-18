package io.genfin.reconciliation.internal.matching;

import io.genfin.reconciliation.matching.MatchCandidate;
import io.genfin.reconciliation.matching.MatchResult;
import io.genfin.reconciliation.matching.MatchingContext;
import io.genfin.reconciliation.port.matching.MatchingStrategy;
import java.time.Duration;
import java.time.Instant;

/**
 * Matches when both candidates carry a timestamp and the gap between them is within {@link
 * MatchingContext#dateTolerance()}. Candidates without a timestamp can never be matched this way.
 */
public final class DateMatch implements MatchingStrategy {

  @Override
  public MatchResult match(MatchCandidate left, MatchCandidate right, MatchingContext context) {
    Instant leftTime = left.occurredAt();
    Instant rightTime = right.occurredAt();
    if (leftTime == null || rightTime == null) {
      return MatchResult.notMatched(left.id(), name());
    }
    Duration gap = Duration.between(leftTime, rightTime).abs();
    if (gap.compareTo(context.dateTolerance()) <= 0) {
      return MatchResult.matched(left.id(), right.id(), name());
    }
    return MatchResult.notMatched(left.id(), name());
  }

  private String name() {
    return "DateMatch";
  }
}
