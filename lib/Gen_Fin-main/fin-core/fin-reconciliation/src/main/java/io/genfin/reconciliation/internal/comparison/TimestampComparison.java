package io.genfin.reconciliation.internal.comparison;

import io.genfin.reconciliation.comparison.ComparisonContext;
import io.genfin.reconciliation.comparison.ComparisonRecord;
import io.genfin.reconciliation.comparison.Difference;
import io.genfin.reconciliation.comparison.DifferenceSeverity;
import io.genfin.reconciliation.comparison.DifferenceType;
import io.genfin.reconciliation.port.comparison.ComparisonStrategy;
import java.time.Duration;
import java.util.List;

/**
 * Reports a difference when both sides carry a timestamp and the gap between them exceeds {@link
 * ComparisonContext#dateTolerance()} — {@link DifferenceSeverity#MINOR} within twice the tolerance,
 * {@link DifferenceSeverity#MAJOR} beyond that.
 */
public final class TimestampComparison implements ComparisonStrategy {

  @Override
  public List<Difference> compare(
      ComparisonRecord left, ComparisonRecord right, ComparisonContext context) {
    if (left.timestamp() == null || right.timestamp() == null) {
      return List.of();
    }
    Duration gap = Duration.between(left.timestamp(), right.timestamp()).abs();
    if (gap.compareTo(context.dateTolerance()) <= 0) {
      return List.of();
    }
    DifferenceSeverity severity =
        gap.compareTo(context.dateTolerance().multipliedBy(2)) <= 0
            ? DifferenceSeverity.MINOR
            : DifferenceSeverity.MAJOR;
    return List.of(
        Difference.of(
            DifferenceType.TIMESTAMP,
            severity,
            "timestamp",
            left.timestamp().toString(),
            right.timestamp().toString(),
            "Timestamps differ by " + gap + "."));
  }
}
