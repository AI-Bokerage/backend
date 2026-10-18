package io.genfin.reconciliation.internal.report;

import io.genfin.api.validation.Validate;
import io.genfin.reconciliation.port.report.ReportFormatter;
import io.genfin.reconciliation.report.ReconciliationReport;
import io.genfin.reconciliation.report.ReportEntry;
import io.genfin.reconciliation.report.ReportSection;

/**
 * Renders a {@link ReconciliationReport} as indented plain text — the trivial default formatter.
 */
public final class PlainTextReportFormatter implements ReportFormatter {

  @Override
  public String format(ReconciliationReport report) {
    Validate.notNull(report, "report must not be null.");
    StringBuilder text = new StringBuilder();
    text.append("Reconciliation ")
        .append(report.reconciliationId().value())
        .append(" (")
        .append(report.generatedAt())
        .append(")")
        .append(System.lineSeparator());
    text.append("  matched=")
        .append(report.summary().matchedCount())
        .append(", partiallyMatched=")
        .append(report.summary().partiallyMatchedCount())
        .append(", unmatched=")
        .append(report.summary().unmatchedCount())
        .append(", discrepancies=")
        .append(report.summary().discrepancyCount())
        .append(System.lineSeparator());
    for (ReportSection section : report.sections()) {
      text.append("  ").append(section.title()).append(':').append(System.lineSeparator());
      for (ReportEntry entry : section.entries()) {
        text.append("    ")
            .append(entry.label())
            .append(": ")
            .append(entry.value())
            .append(System.lineSeparator());
      }
    }
    return text.toString();
  }
}
