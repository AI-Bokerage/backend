package io.genfin.reconciliation.port.matching;

import io.genfin.api.port.spi.Extension;
import io.genfin.reconciliation.matching.MatchCandidate;
import io.genfin.reconciliation.matching.MatchResult;
import io.genfin.reconciliation.matching.MatchingContext;

/**
 * One way of deciding whether two {@link MatchCandidate}s refer to the same financial event —
 * exact, tolerance-based, reference-based, fuzzy, or any composition of those. Implementations
 * never assume a particular provider or record source.
 */
public interface MatchingStrategy extends Extension {

  MatchResult match(MatchCandidate left, MatchCandidate right, MatchingContext context);
}
