package io.genfin.pricing.credit;

import io.genfin.pricing.internal.credit.FullBalanceCreditCalculator;

/** Factory for {@link CreditCalculator} instances. */
public final class CreditCalculators {

  private CreditCalculators() {}

  /** Draws down the wallet's full {@link CreditBalance#total()}, capped at the running amount. */
  public static CreditCalculator fullBalance() {
    return new FullBalanceCreditCalculator();
  }
}
