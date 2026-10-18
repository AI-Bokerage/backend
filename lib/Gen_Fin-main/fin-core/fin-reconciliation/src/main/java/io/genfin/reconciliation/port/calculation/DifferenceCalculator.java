package io.genfin.reconciliation.port.calculation;

import io.genfin.api.port.spi.Extension;
import io.genfin.money.money.Money;
import io.genfin.reconciliation.calculation.AmountDifference;

/**
 * Computes the {@link AmountDifference} between two same-currency amounts — the single place a
 * {@code ComparisonStrategy}, {@code ToleranceCalculator}, or reporting concern goes for a Money
 * gap instead of doing {@code Money} arithmetic itself.
 */
public interface DifferenceCalculator extends Extension {

  AmountDifference difference(Money left, Money right);
}
