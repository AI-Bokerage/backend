package io.genfin.reconciliation.discrepancy;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.reconciliation.comparison.Difference;
import io.genfin.reconciliation.id.DiscrepancyId;

/**
 * A reconciliation-level finding derived from a single {@link Difference} by a {@code
 * DiscrepancyPolicy} — the difference itself only reports what was seen; a discrepancy additionally
 * classifies it (category, severity, reason) and tracks whether it has been dealt with.
 */
public record Discrepancy(
    DiscrepancyId id,
    DiscrepancyCategory category,
    DiscrepancySeverity severity,
    DiscrepancyReason reason,
    DiscrepancyResolution resolution,
    Difference difference,
    String description)
    implements ValueObject {

  public Discrepancy {
    Validate.notNull(id, "id must not be null.");
    Validate.notNull(category, "category must not be null.");
    Validate.notNull(severity, "severity must not be null.");
    Validate.notNull(reason, "reason must not be null.");
    Validate.notNull(resolution, "resolution must not be null.");
    Validate.notNull(difference, "difference must not be null.");
    Validate.notBlank(description, "description must not be blank.");
  }

  public static Discrepancy of(
      DiscrepancyCategory category,
      DiscrepancySeverity severity,
      DiscrepancyReason reason,
      Difference difference,
      String description) {
    return new Discrepancy(
        DiscrepancyId.generate(),
        category,
        severity,
        reason,
        DiscrepancyResolution.UNRESOLVED,
        difference,
        description);
  }

  public boolean isResolved() {
    return resolution != DiscrepancyResolution.UNRESOLVED;
  }

  public Discrepancy resolve(DiscrepancyResolution resolution) {
    Validate.notNull(resolution, "resolution must not be null.");
    return new Discrepancy(id, category, severity, reason, resolution, difference, description);
  }
}
