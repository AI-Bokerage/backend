package io.genfin.reconciliation.port.matching;

import io.genfin.api.port.spi.Extension;
import io.genfin.reconciliation.matching.MatchCandidate;
import io.genfin.reconciliation.matching.MatchResult;
import io.genfin.reconciliation.matching.MatchingContext;
import java.util.List;

/**
 * Matches every candidate on {@code left} (e.g. internal payments) against candidates on {@code
 * right} (e.g. provider settlements) via a {@link MatchingPolicy}, returning exactly one {@link
 * MatchResult} per left candidate — matched, partially matched, or unmatched.
 */
public interface MatchingEngine extends Extension {

  List<MatchResult> match(
      List<MatchCandidate> left, List<MatchCandidate> right, MatchingContext context);
}
