package io.genfin.ledger.port.report;

import io.genfin.api.port.spi.Extension;
import io.genfin.ledger.report.AccountStatement;
import io.genfin.ledger.report.BalanceReport;
import io.genfin.ledger.report.FinancialStatementModel;
import io.genfin.ledger.report.GeneralLedgerReport;
import io.genfin.ledger.report.PostingReport;

/**
 * Renders one of this module's report models into a concrete output format (plain text, CSV, PDF,
 * ...). Only a plain-text default ships with this module; other formats - and any actual document
 * rendering - are the job of a downstream Document Engine.
 */
public interface ReportFormatter extends Extension {

  String format(GeneralLedgerReport report);

  String format(AccountStatement statement);

  String format(PostingReport report);

  String format(BalanceReport report);

  String format(FinancialStatementModel statement);
}
