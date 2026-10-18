package io.genfin.ledger.report;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.util.CollectionUtils;
import io.genfin.api.validation.Validate;
import io.genfin.ledger.id.LedgerId;
import java.time.Instant;
import java.util.List;

/**
 * A reportable view of a {@code Ledger}'s account balances, as computed by {@link
 * io.genfin.ledger.port.balance.BalanceCalculator}. Rendering to a concrete format is the job of a
 * {@link io.genfin.ledger.port.report.ReportFormatter} - this type only holds the data.
 */
public record BalanceReport(
    LedgerId ledgerId, List<ReportSection> sections, ReportSummary summary, Instant generatedAt)
    implements ValueObject {

  public BalanceReport {
    Validate.notNull(ledgerId, "ledgerId must not be null.");
    sections = CollectionUtils.immutableList(sections);
    Validate.notNull(summary, "summary must not be null.");
    Validate.notNull(generatedAt, "generatedAt must not be null.");
  }

  public static BalanceReport of(
      LedgerId ledgerId, List<ReportSection> sections, ReportSummary summary, Instant generatedAt) {
    return new BalanceReport(ledgerId, sections, summary, generatedAt);
  }
}
