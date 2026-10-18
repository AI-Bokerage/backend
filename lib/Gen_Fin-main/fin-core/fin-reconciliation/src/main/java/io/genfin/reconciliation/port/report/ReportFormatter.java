package io.genfin.reconciliation.port.report;

import io.genfin.api.port.spi.Extension;
import io.genfin.reconciliation.report.ReconciliationReport;

/**
 * Renders a {@link ReconciliationReport} into a concrete output format (plain text, CSV, PDF, ...).
 * Only a plain-text default ships with this module; other formats are downstream extensions.
 */
public interface ReportFormatter extends Extension {

  String format(ReconciliationReport report);
}
