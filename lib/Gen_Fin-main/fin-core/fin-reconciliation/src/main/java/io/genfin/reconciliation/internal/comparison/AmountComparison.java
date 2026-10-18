package io.genfin.reconciliation.internal.comparison;

import io.genfin.money.money.Money;
import io.genfin.reconciliation.comparison.ComparisonContext;
import io.genfin.reconciliation.comparison.ComparisonRecord;
import io.genfin.reconciliation.comparison.Difference;
import io.genfin.reconciliation.comparison.DifferenceSeverity;
import io.genfin.reconciliation.comparison.DifferenceType;
import io.genfin.reconciliation.port.comparison.ComparisonStrategy;
import java.util.List;

/**
 * Compares two {@link Money} amounts, exclusively through {@code Money} arithmetic (never {@code
 * BigDecimal} directly). Different currencies are {@link CurrencyComparison}'s concern, not this
 * one's — this strategy stays silent whenever the currencies already differ so the two never
 * double-report the same underlying problem. A gap within {@link
 * ComparisonContext#amountTolerance()} is {@link DifferenceSeverity#MINOR}; anything larger is
 * {@link DifferenceSeverity#MAJOR}.
 */
public final class AmountComparison implements ComparisonStrategy {

  @Override
  public List<Difference> compare(
      ComparisonRecord left, ComparisonRecord right, ComparisonContext context) {
    Money leftAmount = left.amount();
    Money rightAmount = right.amount();
    if (leftAmount == null || rightAmount == null) {
      return List.of();
    }
    if (!leftAmount.currency().equals(rightAmount.currency())) {
      return List.of();
    }
    Money gap = leftAmount.subtract(rightAmount).abs();
    if (gap.isZero()) {
      return List.of();
    }
    Money tolerance = context.amountTolerance();
    DifferenceSeverity severity =
        tolerance != null && gap.compareTo(tolerance) <= 0
            ? DifferenceSeverity.MINOR
            : DifferenceSeverity.MAJOR;
    return List.of(
        Difference.of(
            DifferenceType.AMOUNT,
            severity,
            "amount",
            leftAmount.toString(),
            rightAmount.toString(),
            "Amounts differ by " + gap + "."));
  }
}
