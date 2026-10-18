package io.genfin.reconciliation.comparison;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.reconciliation.id.ComparisonId;
import java.util.List;

/** Every {@link Difference} found between two {@link ComparisonRecord}s under a policy. */
public record ComparisonResult(ComparisonId id, List<Difference> differences)
    implements ValueObject {

  public ComparisonResult {
    Validate.notNull(id, "id must not be null.");
    differences = List.copyOf(differences);
  }

  public static ComparisonResult of(List<Difference> differences) {
    return new ComparisonResult(ComparisonId.generate(), differences);
  }

  public boolean isIdentical() {
    return differences.isEmpty();
  }

  public boolean hasCriticalDifference() {
    return differences.stream().anyMatch(d -> d.severity() == DifferenceSeverity.CRITICAL);
  }
}
