package io.genfin.ledger.report;

import io.genfin.ledger.internal.report.PlainTextReportFormatter;
import io.genfin.ledger.port.report.ReportFormatter;

/**
 * Provides the standard {@link ReportFormatter}, mirroring the module's other {@code *s} factories.
 */
public final class ReportFormatters {

  private static final ReportFormatter STANDARD = new PlainTextReportFormatter();

  private ReportFormatters() {}

  public static ReportFormatter standard() {
    return STANDARD;
  }
}
