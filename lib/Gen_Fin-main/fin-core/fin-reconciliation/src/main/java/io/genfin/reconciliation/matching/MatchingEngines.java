package io.genfin.reconciliation.matching;

import io.genfin.reconciliation.internal.matching.DefaultMatchingEngine;
import io.genfin.reconciliation.port.matching.MatchingEngine;
import io.genfin.reconciliation.port.matching.MatchingPolicy;

/** Factory for the default {@link MatchingEngine} implementation. */
public final class MatchingEngines {

  private MatchingEngines() {}

  /** An engine driven by {@link MatchingPolicies#standard()}. */
  public static MatchingEngine standard() {
    return new DefaultMatchingEngine(MatchingPolicies.standard());
  }

  public static MatchingEngine of(MatchingPolicy policy) {
    return new DefaultMatchingEngine(policy);
  }
}
