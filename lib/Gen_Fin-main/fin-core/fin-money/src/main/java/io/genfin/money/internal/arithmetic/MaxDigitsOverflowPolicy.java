package io.genfin.money.internal.arithmetic;

import io.genfin.money.currency.Currency;
import io.genfin.money.exception.MoneyOverflowException;
import io.genfin.money.port.arithmetic.OverflowPolicy;
import java.math.BigDecimal;

/** Rejects values whose unscaled precision exceeds a configured digit ceiling. */
public final class MaxDigitsOverflowPolicy implements OverflowPolicy {

  private final int maxDigits;

  public MaxDigitsOverflowPolicy(int maxDigits) {
    this.maxDigits = maxDigits;
  }

  @Override
  public void check(BigDecimal value, Currency currency) {
    if (value.precision() > maxDigits) {
      throw new MoneyOverflowException(
          "Amount "
              + value
              + " "
              + currency.code()
              + " exceeds "
              + maxDigits
              + " significant digits.");
    }
  }
}
