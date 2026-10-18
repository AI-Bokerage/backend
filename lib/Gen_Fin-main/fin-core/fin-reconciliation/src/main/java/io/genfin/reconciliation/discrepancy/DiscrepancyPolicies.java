package io.genfin.reconciliation.discrepancy;

import io.genfin.reconciliation.internal.discrepancy.DefaultDiscrepancyPolicy;
import io.genfin.reconciliation.port.discrepancy.DiscrepancyPolicy;

/** Factory for the default {@link DiscrepancyPolicy} implementation. */
public final class DiscrepancyPolicies {

  private DiscrepancyPolicies() {}

  public static DiscrepancyPolicy standard() {
    return new DefaultDiscrepancyPolicy();
  }
}
