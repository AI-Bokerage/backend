package io.genfin.reconciliation.port.matching;

import io.genfin.api.port.spi.Extension;
import io.genfin.reconciliation.matching.MatchCandidate;
import io.genfin.reconciliation.matching.MatchResult;
import io.genfin.reconciliation.matching.MatchingContext;

/**
 * The single entry point for deciding whether one pair of candidates matches: composes an ordered
 * set of {@link MatchingStrategy} implementations so callers evaluate one policy instead of wiring
 * each strategy themselves.
 */
public interface MatchingPolicy extends Extension {

  MatchResult evaluate(MatchCandidate left, MatchCandidate right, MatchingContext context);
}
