package io.genfin.ledger.port.calculation;

import io.genfin.api.port.spi.Extension;
import io.genfin.money.money.Money;
import io.genfin.money.percentage.Percentage;
import java.util.Optional;

/**
 * Expresses how far {@code actual} has drifted from {@code base} as a {@link Percentage} - e.g. for
 * a variance line on a {@link io.genfin.ledger.report.BalanceReport}. Returns {@link
 * Optional#empty()} when {@code base} is zero, since a variance ratio is not meaningful against a
 * zero base. Mirrors {@code io.genfin.reconciliation.port.calculation.VarianceCalculator}.
 */
public interface VarianceCalculator extends Extension {

  Optional<Percentage> varianceOf(Money base, Money actual);
}
