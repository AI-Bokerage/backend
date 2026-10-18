package io.genfin.reconciliation.discrepancy;

import io.genfin.reconciliation.internal.discrepancy.DefaultDiscrepancyDetector;
import io.genfin.reconciliation.port.discrepancy.DiscrepancyDetector;
import io.genfin.reconciliation.port.discrepancy.DiscrepancyPolicy;

/** Factory for the default {@link DiscrepancyDetector} implementation. */
public final class DiscrepancyDetectors {

  private DiscrepancyDetectors() {}

  /** The standard detector, driven by {@link DiscrepancyPolicies#standard()}. */
  public static DiscrepancyDetector standard() {
    return new DefaultDiscrepancyDetector(DiscrepancyPolicies.standard());
  }

  public static DiscrepancyDetector of(DiscrepancyPolicy policy) {
    return new DefaultDiscrepancyDetector(policy);
  }
}
