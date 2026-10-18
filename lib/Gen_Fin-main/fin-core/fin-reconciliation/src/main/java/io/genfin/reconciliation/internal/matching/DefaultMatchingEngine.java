package io.genfin.reconciliation.internal.matching;

import io.genfin.api.validation.Validate;
import io.genfin.reconciliation.matching.MatchCandidate;
import io.genfin.reconciliation.matching.MatchResult;
import io.genfin.reconciliation.matching.MatchingContext;
import io.genfin.reconciliation.port.matching.MatchingEngine;
import io.genfin.reconciliation.port.matching.MatchingPolicy;
import java.util.ArrayList;
import java.util.List;

/**
 * Greedily pairs each {@code left} candidate with the best still-available {@code right} candidate,
 * per {@link MatchingPolicy}, preferring the highest-confidence match. Every left candidate is
 * consumed exactly once and matched right candidates are removed from the pool so no right
 * candidate is reused across two matches.
 *
 * <p>ponytail: greedy nearest-match, O(n*m) per run; upgrade to an optimal bipartite assignment
 * (e.g. Hungarian algorithm) only if greedy mismatches show up in practice.
 */
public final class DefaultMatchingEngine implements MatchingEngine {

  private final MatchingPolicy policy;

  public DefaultMatchingEngine(MatchingPolicy policy) {
    this.policy = Validate.notNull(policy, "policy must not be null.");
  }

  @Override
  public List<MatchResult> match(
      List<MatchCandidate> left, List<MatchCandidate> right, MatchingContext context) {
    Validate.notNull(left, "left must not be null.");
    Validate.notNull(right, "right must not be null.");
    Validate.notNull(context, "context must not be null.");

    List<MatchCandidate> pool = new ArrayList<>(right);
    List<MatchResult> results = new ArrayList<>(left.size());
    for (MatchCandidate candidate : left) {
      results.add(bestMatchAndConsume(candidate, pool, context));
    }
    return List.copyOf(results);
  }

  private MatchResult bestMatchAndConsume(
      MatchCandidate candidate, List<MatchCandidate> pool, MatchingContext context) {
    MatchResult best = null;
    int bestIndex = -1;
    for (int i = 0; i < pool.size(); i++) {
      MatchResult result = policy.evaluate(candidate, pool.get(i), context);
      if (result.isMatch() && (best == null || result.confidence() > best.confidence())) {
        best = result;
        bestIndex = i;
      }
    }
    if (best == null) {
      return MatchResult.notMatched(candidate.id(), "MatchingEngine");
    }
    pool.remove(bestIndex);
    return best;
  }
}
