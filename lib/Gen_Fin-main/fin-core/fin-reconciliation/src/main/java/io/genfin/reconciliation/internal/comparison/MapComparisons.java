package io.genfin.reconciliation.internal.comparison;

import io.genfin.reconciliation.comparison.Difference;
import io.genfin.reconciliation.comparison.DifferenceSeverity;
import io.genfin.reconciliation.comparison.DifferenceType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared key-by-key comparison behind {@link MetadataComparison} and {@link AttributeComparison}.
 */
final class MapComparisons {

  private MapComparisons() {}

  static List<Difference> compare(
      Map<String, String> left, Map<String, String> right, DifferenceType type) {
    Set<String> keys = new LinkedHashSet<>(left.keySet());
    keys.addAll(right.keySet());
    List<Difference> differences = new ArrayList<>();
    for (String key : keys) {
      String leftValue = left.get(key);
      String rightValue = right.get(key);
      if (!java.util.Objects.equals(leftValue, rightValue)) {
        differences.add(
            Difference.of(
                type,
                DifferenceSeverity.MINOR,
                key,
                leftValue,
                rightValue,
                "Values for '" + key + "' differ."));
      }
    }
    return differences;
  }
}
