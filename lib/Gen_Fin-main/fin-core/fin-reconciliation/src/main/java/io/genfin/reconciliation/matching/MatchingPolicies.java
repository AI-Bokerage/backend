package io.genfin.reconciliation.matching;

import io.genfin.reconciliation.internal.matching.DefaultMatchingPolicy;
import io.genfin.reconciliation.port.matching.MatchingPolicy;
import io.genfin.reconciliation.port.matching.MatchingStrategy;
import java.util.List;

/** Factory for the default {@link MatchingPolicy} implementation. */
public final class MatchingPolicies {

  private MatchingPolicies() {}

  /** The standard policy: {@link MatchingStrategies#standardChain()} tried in order. */
  public static MatchingPolicy standard() {
    return new DefaultMatchingPolicy(MatchingStrategies.standardChain());
  }

  public static MatchingPolicy chain(List<MatchingStrategy> orderedStrategies) {
    return new DefaultMatchingPolicy(orderedStrategies);
  }
}
