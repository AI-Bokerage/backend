package io.genfin.reconciliation.report;

import io.genfin.reconciliation.internal.report.PlainTextReportFormatter;
import io.genfin.reconciliation.port.report.ReportFormatter;

/**
 * Provides the standard {@link ReportFormatter}, mirroring the module's other {@code *s}
 * registries.
 */
public final class ReportFormatters {

  private static final ReportFormatter STANDARD = new PlainTextReportFormatter();

  private ReportFormatters() {}

  public static ReportFormatter standard() {
    return STANDARD;
  }
}
