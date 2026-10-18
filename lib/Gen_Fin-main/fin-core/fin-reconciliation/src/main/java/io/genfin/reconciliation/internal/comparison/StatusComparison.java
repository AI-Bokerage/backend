package io.genfin.reconciliation.internal.comparison;

import io.genfin.reconciliation.comparison.ComparisonContext;
import io.genfin.reconciliation.comparison.ComparisonRecord;
import io.genfin.reconciliation.comparison.Difference;
import io.genfin.reconciliation.comparison.DifferenceSeverity;
import io.genfin.reconciliation.comparison.DifferenceType;
import io.genfin.reconciliation.port.comparison.ComparisonStrategy;
import java.util.List;

/**
 * Reports a {@link DifferenceSeverity#MAJOR} difference when both sides carry a status code and the
 * codes are not equal (case-insensitive, since different systems capitalize differently).
 */
public final class StatusComparison implements ComparisonStrategy {

  @Override
  public List<Difference> compare(
      ComparisonRecord left, ComparisonRecord right, ComparisonContext context) {
    if (left.status() == null || right.status() == null) {
      return List.of();
    }
    if (left.status().equalsIgnoreCase(right.status())) {
      return List.of();
    }
    return List.of(
        Difference.of(
            DifferenceType.STATUS,
            DifferenceSeverity.MAJOR,
            "status",
            left.status(),
            right.status(),
            "Statuses differ."));
  }
}
