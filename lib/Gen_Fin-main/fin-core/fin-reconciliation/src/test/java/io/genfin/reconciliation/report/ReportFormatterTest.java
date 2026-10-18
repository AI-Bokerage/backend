package io.genfin.reconciliation.report;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.reconciliation.id.ReconciliationId;
import io.genfin.reconciliation.port.report.ReportFormatter;
import io.genfin.reconciliation.summary.ReconciliationStatistics;
import io.genfin.reconciliation.summary.ReconciliationSummary;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReportFormatterTest {

  @Test
  void plainTextFormatterRendersSummaryAndSections() {
    ReconciliationSummary summary =
        new ReconciliationSummary(
            2, 1, 1, 1, Map.of(), Map.of(), new ReconciliationStatistics(4, 4, 1, 0.5));
    ReportSection section =
        ReportSection.of("Unmatched", List.of(ReportEntry.of("item", "REC-ITEM-1")));
    ReconciliationReport report =
        ReconciliationReport.of(
            ReconciliationId.of("REC-1"),
            summary,
            List.of(section),
            Instant.parse("2026-07-31T00:00:00Z"));

    ReportFormatter formatter = ReportFormatters.standard();
    String text = formatter.format(report);

    assertThat(text)
        .contains("REC-1")
        .contains("matched=2")
        .contains("Unmatched:")
        .contains("item: REC-ITEM-1");
  }
}
