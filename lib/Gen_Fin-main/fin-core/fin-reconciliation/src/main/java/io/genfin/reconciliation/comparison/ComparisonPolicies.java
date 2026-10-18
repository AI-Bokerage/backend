package io.genfin.reconciliation.comparison;

import io.genfin.reconciliation.internal.comparison.DefaultComparisonPolicy;
import io.genfin.reconciliation.port.comparison.ComparisonPolicy;
import io.genfin.reconciliation.port.comparison.ComparisonStrategy;
import java.util.List;

/** Factory for the default {@link ComparisonPolicy} implementation. */
public final class ComparisonPolicies {

  private ComparisonPolicies() {}

  /** The standard policy: every {@link ComparisonStrategies#standardChain()} strategy runs. */
  public static ComparisonPolicy standard() {
    return new DefaultComparisonPolicy(ComparisonStrategies.standardChain());
  }

  public static ComparisonPolicy of(List<ComparisonStrategy> strategies) {
    return new DefaultComparisonPolicy(strategies);
  }
}
