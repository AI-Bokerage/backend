package io.genfin.ledger.balance;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.ledger.internal.balance.DefaultBalanceCalculator;
import io.genfin.ledger.internal.balance.DefaultBalancePolicy;
import io.genfin.ledger.port.balance.BalanceCalculator;
import io.genfin.ledger.port.balance.BalancePolicy;

/** Factory for {@link BalanceCalculator} instances. */
public final class BalanceCalculators {

  private static final BalancePolicy STANDARD_POLICY = new DefaultBalancePolicy();

  private BalanceCalculators() {}

  /** Includes every entry that reached {@code POSTED} or a later, non-{@code FAILED} status. */
  public static BalancePolicy standardPolicy() {
    return STANDARD_POLICY;
  }

  public static BalanceCalculator of(BalancePolicy policy) {
    return new DefaultBalanceCalculator(policy);
  }

  /**
   * Resolves the {@link BalanceCalculator} registered in {@code registry} if present; otherwise
   * builds one from the registered {@link BalancePolicy}, falling back to {@link #standardPolicy()}
   * when none is registered.
   */
  public static BalanceCalculator from(ExtensionRegistry registry) {
    return registry
        .find(BalanceCalculator.class)
        .orElseGet(
            () ->
                of(
                    registry
                        .find(BalancePolicy.class)
                        .orElseGet(BalanceCalculators::standardPolicy)));
  }
}
