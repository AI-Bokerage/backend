package io.genfin.reconciliation.report;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.reconciliation.id.ReconciliationId;
import io.genfin.reconciliation.summary.ReconciliationSummary;
import java.time.Instant;
import java.util.List;

/**
 * A reportable view of one reconciliation: its {@link ReconciliationSummary} plus any number of
 * detail {@link ReportSection}s (e.g. unmatched items, open discrepancies). Rendering to a concrete
 * format (plain text, CSV, PDF, ...) is the job of a {@code ReportFormatter} — this type only holds
 * the data.
 */
public record ReconciliationReport(
    ReconciliationId reconciliationId,
    ReconciliationSummary summary,
    List<ReportSection> sections,
    Instant generatedAt)
    implements ValueObject {

  public ReconciliationReport {
    Validate.notNull(reconciliationId, "reconciliationId must not be null.");
    Validate.notNull(summary, "summary must not be null.");
    Validate.notNull(generatedAt, "generatedAt must not be null.");
    sections = List.copyOf(sections);
  }

  public static ReconciliationReport of(
      ReconciliationId reconciliationId,
      ReconciliationSummary summary,
      List<ReportSection> sections,
      Instant generatedAt) {
    return new ReconciliationReport(reconciliationId, summary, sections, generatedAt);
  }
}
