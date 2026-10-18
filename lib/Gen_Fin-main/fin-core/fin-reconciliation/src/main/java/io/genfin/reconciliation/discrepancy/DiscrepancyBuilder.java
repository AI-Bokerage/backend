package io.genfin.reconciliation.discrepancy;

import io.genfin.reconciliation.comparison.Difference;
import io.genfin.reconciliation.id.DiscrepancyId;

/**
 * Builds {@link Discrepancy} records. Preferred over the canonical constructor for readability at
 * call sites carrying this many fields.
 */
public final class DiscrepancyBuilder {

  private DiscrepancyId id;
  private DiscrepancyCategory category;
  private DiscrepancySeverity severity;
  private DiscrepancyReason reason;
  private DiscrepancyResolution resolution = DiscrepancyResolution.UNRESOLVED;
  private Difference difference;
  private String description;

  private DiscrepancyBuilder() {}

  public static DiscrepancyBuilder newDiscrepancy() {
    return new DiscrepancyBuilder();
  }

  public DiscrepancyBuilder id(DiscrepancyId id) {
    this.id = id;
    return this;
  }

  public DiscrepancyBuilder category(DiscrepancyCategory category) {
    this.category = category;
    return this;
  }

  public DiscrepancyBuilder severity(DiscrepancySeverity severity) {
    this.severity = severity;
    return this;
  }

  public DiscrepancyBuilder reason(DiscrepancyReason reason) {
    this.reason = reason;
    return this;
  }

  public DiscrepancyBuilder resolution(DiscrepancyResolution resolution) {
    this.resolution = resolution;
    return this;
  }

  public DiscrepancyBuilder difference(Difference difference) {
    this.difference = difference;
    return this;
  }

  public DiscrepancyBuilder description(String description) {
    this.description = description;
    return this;
  }

  public Discrepancy build() {
    return new Discrepancy(
        id == null ? DiscrepancyId.generate() : id,
        category,
        severity,
        reason,
        resolution,
        difference,
        description);
  }
}
