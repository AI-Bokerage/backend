package io.genfin.reconciliation.report;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import java.util.List;

/** A titled group of {@link ReportEntry} lines within a {@link ReconciliationReport}. */
public record ReportSection(String title, List<ReportEntry> entries) implements ValueObject {

  public ReportSection {
    Validate.notBlank(title, "title must not be blank.");
    entries = List.copyOf(entries);
  }

  public static ReportSection of(String title, List<ReportEntry> entries) {
    return new ReportSection(title, entries);
  }
}
