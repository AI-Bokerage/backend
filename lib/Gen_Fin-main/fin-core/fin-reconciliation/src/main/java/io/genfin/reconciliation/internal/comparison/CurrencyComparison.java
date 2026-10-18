package io.genfin.reconciliation.internal.comparison;

import io.genfin.reconciliation.comparison.ComparisonContext;
import io.genfin.reconciliation.comparison.ComparisonRecord;
import io.genfin.reconciliation.comparison.Difference;
import io.genfin.reconciliation.comparison.DifferenceSeverity;
import io.genfin.reconciliation.comparison.DifferenceType;
import io.genfin.reconciliation.port.comparison.ComparisonStrategy;
import java.util.List;

/**
 * Reports a {@link DifferenceSeverity#CRITICAL} difference when both sides carry an amount but in
 * different currencies — no tolerance ever applies here, and {@link AmountComparison} skips its own
 * check once this fires to avoid comparing incomparable amounts.
 */
public final class CurrencyComparison implements ComparisonStrategy {

  @Override
  public List<Difference> compare(
      ComparisonRecord left, ComparisonRecord right, ComparisonContext context) {
    if (left.amount() == null || right.amount() == null) {
      return List.of();
    }
    if (left.amount().currency().equals(right.amount().currency())) {
      return List.of();
    }
    return List.of(
        Difference.of(
            DifferenceType.CURRENCY,
            DifferenceSeverity.CRITICAL,
            "currency",
            left.amount().currency().code(),
            right.amount().currency().code(),
            "Currencies differ."));
  }
}
