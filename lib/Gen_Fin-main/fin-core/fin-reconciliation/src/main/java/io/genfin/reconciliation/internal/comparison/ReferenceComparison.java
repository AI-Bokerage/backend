package io.genfin.reconciliation.internal.comparison;

import io.genfin.reconciliation.comparison.ComparisonContext;
import io.genfin.reconciliation.comparison.ComparisonRecord;
import io.genfin.reconciliation.comparison.Difference;
import io.genfin.reconciliation.comparison.DifferenceSeverity;
import io.genfin.reconciliation.comparison.DifferenceType;
import io.genfin.reconciliation.port.comparison.ComparisonStrategy;
import java.util.List;

/** Reports a {@link DifferenceSeverity#MAJOR} difference when the two references are not equal. */
public final class ReferenceComparison implements ComparisonStrategy {

  @Override
  public List<Difference> compare(
      ComparisonRecord left, ComparisonRecord right, ComparisonContext context) {
    if (left.reference() == null || right.reference() == null) {
      return List.of();
    }
    if (left.reference().equals(right.reference())) {
      return List.of();
    }
    return List.of(
        Difference.of(
            DifferenceType.REFERENCE,
            DifferenceSeverity.MAJOR,
            "reference",
            left.reference().toString(),
            right.reference().toString(),
            "References differ."));
  }
}
