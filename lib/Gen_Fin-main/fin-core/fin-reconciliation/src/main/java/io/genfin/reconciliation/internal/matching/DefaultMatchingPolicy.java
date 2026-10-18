package io.genfin.reconciliation.internal.matching;

import io.genfin.api.validation.Validate;
import io.genfin.reconciliation.matching.MatchCandidate;
import io.genfin.reconciliation.matching.MatchResult;
import io.genfin.reconciliation.matching.MatchingContext;
import io.genfin.reconciliation.port.matching.MatchingPolicy;
import io.genfin.reconciliation.port.matching.MatchingStrategy;
import java.util.List;

/**
 * Tries each configured {@link MatchingStrategy} in order and accepts the first one that matches —
 * the same any-of behaviour as {@link CompositeMatch}, exposed as the policy entry point so callers
 * depend on one SPI instead of wiring the chain themselves.
 */
public final class DefaultMatchingPolicy implements MatchingPolicy {

  private final MatchingStrategy chain;

  public DefaultMatchingPolicy(List<MatchingStrategy> orderedStrategies) {
    Validate.notNull(orderedStrategies, "orderedStrategies must not be null.");
    this.chain = new CompositeMatch(orderedStrategies, false);
  }

  @Override
  public MatchResult evaluate(MatchCandidate left, MatchCandidate right, MatchingContext context) {
    return chain.match(left, right, context);
  }
}
