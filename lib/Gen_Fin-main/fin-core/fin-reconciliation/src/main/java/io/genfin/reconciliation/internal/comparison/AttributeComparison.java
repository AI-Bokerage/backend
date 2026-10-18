package io.genfin.reconciliation.internal.comparison;

import io.genfin.reconciliation.comparison.ComparisonContext;
import io.genfin.reconciliation.comparison.ComparisonRecord;
import io.genfin.reconciliation.comparison.Difference;
import io.genfin.reconciliation.comparison.DifferenceType;
import io.genfin.reconciliation.port.comparison.ComparisonStrategy;
import java.util.List;

/**
 * Reports one {@link DifferenceType#ATTRIBUTE} difference per application-defined custom attribute
 * that is present with a different value on either side, or present on only one side. Kept separate
 * from {@link MetadataComparison} so callers can attach a different policy/severity to custom
 * attributes than to system metadata if they ever need to.
 */
public final class AttributeComparison implements ComparisonStrategy {

  @Override
  public List<Difference> compare(
      ComparisonRecord left, ComparisonRecord right, ComparisonContext context) {
    return MapComparisons.compare(left.attributes(), right.attributes(), DifferenceType.ATTRIBUTE);
  }
}
