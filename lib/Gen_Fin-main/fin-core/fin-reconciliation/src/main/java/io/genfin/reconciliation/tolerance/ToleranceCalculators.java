package io.genfin.reconciliation.tolerance;

import io.genfin.reconciliation.internal.tolerance.DefaultCustomToleranceRuleRegistry;
import io.genfin.reconciliation.internal.tolerance.DefaultToleranceCalculator;
import io.genfin.reconciliation.port.tolerance.CustomToleranceRuleRegistry;
import io.genfin.reconciliation.port.tolerance.ToleranceCalculator;

/** Factory for the default {@link ToleranceCalculator} implementation. */
public final class ToleranceCalculators {

  private ToleranceCalculators() {}

  /** A standard calculator backed by a fresh, empty {@link CustomToleranceRuleRegistry}. */
  public static ToleranceCalculator standard() {
    return of(new DefaultCustomToleranceRuleRegistry());
  }

  public static ToleranceCalculator of(CustomToleranceRuleRegistry customRules) {
    return new DefaultToleranceCalculator(customRules);
  }

  public static CustomToleranceRuleRegistry newCustomRuleRegistry() {
    return new DefaultCustomToleranceRuleRegistry();
  }
}
