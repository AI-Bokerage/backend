package io.genfin.reconciliation.internal.comparison;

import io.genfin.reconciliation.comparison.ComparisonContext;
import io.genfin.reconciliation.comparison.ComparisonRecord;
import io.genfin.reconciliation.comparison.Difference;
import io.genfin.reconciliation.comparison.DifferenceType;
import io.genfin.reconciliation.port.comparison.ComparisonStrategy;
import java.util.List;

/**
 * Reports one {@link DifferenceType#METADATA} difference per key that is present with a different
 * value on either side, or present on only one side.
 */
public final class MetadataComparison implements ComparisonStrategy {

  @Override
  public List<Difference> compare(
      ComparisonRecord left, ComparisonRecord right, ComparisonContext context) {
    return MapComparisons.compare(left.metadata(), right.metadata(), DifferenceType.METADATA);
  }
}
