package io.genfin.ledger.internal.report;

import io.genfin.api.validation.Validate;
import io.genfin.ledger.port.report.ReportFormatter;
import io.genfin.ledger.report.AccountStatement;
import io.genfin.ledger.report.BalanceReport;
import io.genfin.ledger.report.FinancialStatementModel;
import io.genfin.ledger.report.GeneralLedgerReport;
import io.genfin.ledger.report.PostingReport;
import io.genfin.ledger.report.ReportEntry;
import io.genfin.ledger.report.ReportSection;
import io.genfin.ledger.report.ReportSummary;

/** Renders this module's report models as indented plain text - the trivial default formatter. */
public final class PlainTextReportFormatter implements ReportFormatter {

  @Override
  public String format(GeneralLedgerReport report) {
    Validate.notNull(report, "report must not be null.");
    StringBuilder text = new StringBuilder();
    text.append("General Ledger ")
        .append(report.ledgerId().value())
        .append(" / period ")
        .append(report.periodId().value())
        .append(" (")
        .append(report.generatedAt())
        .append(")")
        .append(System.lineSeparator());
    appendSummary(text, report.summary());
    appendSections(text, report.sections());
    return text.toString();
  }

  @Override
  public String format(AccountStatement statement) {
    Validate.notNull(statement, "statement must not be null.");
    StringBuilder text = new StringBuilder();
    text.append("Account Statement ")
        .append(statement.accountId().value())
        .append(" / period ")
        .append(statement.periodId().value())
        .append(" (")
        .append(statement.generatedAt())
        .append(")")
        .append(System.lineSeparator());
    text.append("  opening: ").append(statement.openingBalance()).append(System.lineSeparator());
    for (ReportEntry line : statement.lines()) {
      text.append("  ")
          .append(line.label())
          .append(": ")
          .append(line.value())
          .append(System.lineSeparator());
    }
    text.append("  closing: ").append(statement.closingBalance()).append(System.lineSeparator());
    return text.toString();
  }

  @Override
  public String format(PostingReport report) {
    Validate.notNull(report, "report must not be null.");
    StringBuilder text = new StringBuilder();
    text.append("Posting Report ")
        .append(report.ledgerId().value())
        .append(" (")
        .append(report.generatedAt())
        .append(")")
        .append(System.lineSeparator());
    appendSummary(text, report.summary());
    appendSections(text, report.sections());
    return text.toString();
  }

  @Override
  public String format(BalanceReport report) {
    Validate.notNull(report, "report must not be null.");
    StringBuilder text = new StringBuilder();
    text.append("Balance Report ")
        .append(report.ledgerId().value())
        .append(" (")
        .append(report.generatedAt())
        .append(")")
        .append(System.lineSeparator());
    appendSummary(text, report.summary());
    appendSections(text, report.sections());
    return text.toString();
  }

  @Override
  public String format(FinancialStatementModel statement) {
    Validate.notNull(statement, "statement must not be null.");
    StringBuilder text = new StringBuilder();
    text.append(statement.name())
        .append(" ")
        .append(statement.ledgerId().value())
        .append(" (")
        .append(statement.generatedAt())
        .append(")")
        .append(System.lineSeparator());
    appendSummary(text, statement.summary());
    appendSections(text, statement.sections());
    return text.toString();
  }

  private void appendSummary(StringBuilder text, ReportSummary summary) {
    text.append("  lines=")
        .append(summary.lineCount())
        .append(", totalDebit=")
        .append(summary.totalDebit())
        .append(", totalCredit=")
        .append(summary.totalCredit())
        .append(System.lineSeparator());
  }

  private void appendSections(StringBuilder text, Iterable<ReportSection> sections) {
    for (ReportSection section : sections) {
      text.append("  ").append(section.title()).append(':').append(System.lineSeparator());
      for (ReportEntry entry : section.entries()) {
        text.append("    ")
            .append(entry.label())
            .append(": ")
            .append(entry.value())
            .append(System.lineSeparator());
      }
    }
  }
}
