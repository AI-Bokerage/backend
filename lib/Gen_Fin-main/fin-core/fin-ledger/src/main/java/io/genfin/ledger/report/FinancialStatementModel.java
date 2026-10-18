package io.genfin.ledger.report;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.util.CollectionUtils;
import io.genfin.api.validation.Validate;
import io.genfin.ledger.id.LedgerId;
import java.time.Instant;
import java.util.List;

/**
 * A reportable financial statement (e.g. a balance sheet or an income statement) made up of
 * caller-named {@link ReportSection}s - Gen-Fin hardcodes no statement types or account groupings,
 * {@code name} and every section title are application-supplied. Rendering to a concrete format is
 * the job of a {@link io.genfin.ledger.port.report.ReportFormatter} - this type only holds the
 * data.
 */
public record FinancialStatementModel(
    LedgerId ledgerId,
    String name,
    List<ReportSection> sections,
    ReportSummary summary,
    Instant generatedAt)
    implements ValueObject {

  public FinancialStatementModel {
    Validate.notNull(ledgerId, "ledgerId must not be null.");
    Validate.notBlank(name, "name must not be blank.");
    sections = CollectionUtils.immutableList(sections);
    Validate.notNull(summary, "summary must not be null.");
    Validate.notNull(generatedAt, "generatedAt must not be null.");
  }

  public static FinancialStatementModel of(
      LedgerId ledgerId,
      String name,
      List<ReportSection> sections,
      ReportSummary summary,
      Instant generatedAt) {
    return new FinancialStatementModel(ledgerId, name, sections, summary, generatedAt);
  }
}
