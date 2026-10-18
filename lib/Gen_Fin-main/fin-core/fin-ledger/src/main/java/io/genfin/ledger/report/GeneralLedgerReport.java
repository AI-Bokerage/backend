package io.genfin.ledger.report;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.util.CollectionUtils;
import io.genfin.api.validation.Validate;
import io.genfin.ledger.id.AccountingPeriodId;
import io.genfin.ledger.id.LedgerId;
import java.time.Instant;
import java.util.List;

/**
 * A reportable view of every account's activity in one {@code Ledger} over one {@link
 * io.genfin.ledger.period.AccountingPeriod}, typically one {@link ReportSection} per account.
 * Rendering to a concrete format is the job of a {@link
 * io.genfin.ledger.port.report.ReportFormatter} - this type only holds the data.
 */
public record GeneralLedgerReport(
    LedgerId ledgerId,
    AccountingPeriodId periodId,
    List<ReportSection> sections,
    ReportSummary summary,
    Instant generatedAt)
    implements ValueObject {

  public GeneralLedgerReport {
    Validate.notNull(ledgerId, "ledgerId must not be null.");
    Validate.notNull(periodId, "periodId must not be null.");
    sections = CollectionUtils.immutableList(sections);
    Validate.notNull(summary, "summary must not be null.");
    Validate.notNull(generatedAt, "generatedAt must not be null.");
  }

  public static GeneralLedgerReport of(
      LedgerId ledgerId,
      AccountingPeriodId periodId,
      List<ReportSection> sections,
      ReportSummary summary,
      Instant generatedAt) {
    return new GeneralLedgerReport(ledgerId, periodId, sections, summary, generatedAt);
  }
}
