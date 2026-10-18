package io.genfin.ledger.report;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.util.CollectionUtils;
import io.genfin.api.validation.Validate;
import java.util.List;

/**
 * A titled group of {@link ReportEntry} lines within one of this package's report models (e.g. one
 * account's activity within a {@link GeneralLedgerReport}). Mirrors {@code
 * io.genfin.reconciliation.report.ReportSection}.
 */
public record ReportSection(String title, List<ReportEntry> entries) implements ValueObject {

  public ReportSection {
    Validate.notBlank(title, "title must not be blank.");
    entries = CollectionUtils.immutableList(entries);
  }

  public static ReportSection of(String title, List<ReportEntry> entries) {
    return new ReportSection(title, entries);
  }
}
