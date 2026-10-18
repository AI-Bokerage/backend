package io.genfin.ledger.trialbalance;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.ledger.internal.trialbalance.DefaultTrialBalanceCalculator;
import io.genfin.ledger.port.trialbalance.TrialBalanceCalculator;

/**
 * Factory for {@link TrialBalanceCalculator} instances. Mirrors {@code
 * io.genfin.ledger.balance.BalanceCalculators}.
 */
public final class TrialBalanceCalculators {

  private TrialBalanceCalculators() {}

  public static TrialBalanceCalculator standard() {
    return new DefaultTrialBalanceCalculator();
  }

  public static TrialBalanceCalculator from(ExtensionRegistry registry) {
    return registry.find(TrialBalanceCalculator.class).orElseGet(TrialBalanceCalculators::standard);
  }
}
