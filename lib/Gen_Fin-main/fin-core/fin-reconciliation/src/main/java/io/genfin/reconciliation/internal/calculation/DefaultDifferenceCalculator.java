package io.genfin.reconciliation.internal.calculation;

import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.reconciliation.calculation.AmountDifference;
import io.genfin.reconciliation.port.calculation.DifferenceCalculator;

/** The standard {@link DifferenceCalculator}: plain {@link Money} subtraction, nothing more. */
public final class DefaultDifferenceCalculator implements DifferenceCalculator {

  @Override
  public AmountDifference difference(Money left, Money right) {
    Validate.notNull(left, "left must not be null.");
    Validate.notNull(right, "right must not be null.");
    Validate.required(
        left.currency().equals(right.currency()), "left and right must share a currency.");
    Money signed = left.subtract(right);
    return new AmountDifference(signed, signed.abs());
  }
}
