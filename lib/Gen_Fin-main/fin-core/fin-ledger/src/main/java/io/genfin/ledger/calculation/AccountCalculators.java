package io.genfin.ledger.calculation;

import io.genfin.ledger.internal.calculation.DefaultAccountCalculator;
import io.genfin.ledger.port.calculation.AccountCalculator;

/**
 * Provides the standard {@link AccountCalculator}, mirroring the module's other {@code *s}
 * factories.
 */
public final class AccountCalculators {

  private static final AccountCalculator STANDARD = new DefaultAccountCalculator();

  private AccountCalculators() {}

  public static AccountCalculator standard() {
    return STANDARD;
  }
}
