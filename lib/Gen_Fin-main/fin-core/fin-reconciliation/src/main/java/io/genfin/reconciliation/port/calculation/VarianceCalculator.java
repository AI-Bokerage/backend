package io.genfin.reconciliation.port.calculation;

import io.genfin.api.port.spi.Extension;
import io.genfin.money.money.Money;
import io.genfin.money.percentage.Percentage;
import java.util.Optional;

/**
 * Expresses how far {@code actual} has drifted from {@code base} as a {@link Percentage} — e.g. for
 * a {@code PercentageTolerance} check or a summary report line. Returns {@link Optional#empty()}
 * when {@code base} is zero, since a variance ratio is not meaningful against a zero base.
 */
public interface VarianceCalculator extends Extension {

  Optional<Percentage> varianceOf(Money base, Money actual);
}
