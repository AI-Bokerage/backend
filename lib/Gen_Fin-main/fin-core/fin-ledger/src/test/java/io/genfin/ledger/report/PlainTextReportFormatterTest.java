package io.genfin.ledger.report;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.ledger.id.LedgerId;
import io.genfin.ledger.port.report.ReportFormatter;
import io.genfin.money.currency.Currency;
import io.genfin.money.currency.CurrencyFactory;
import io.genfin.money.money.Money;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlainTextReportFormatterTest {

  private static final Currency USD =
      CurrencyFactory.newCurrency().code("USD").symbol("$").displayName("US Dollar").build();

  @Test
  void rendersEverySectionAndItsEntries() {
    ReportFormatter formatter = ReportFormatters.standard();
    ReportSection section =
        ReportSection.of("Cash", List.of(ReportEntry.of("Opening", "0.00 USD")));
    BalanceReport report =
        BalanceReport.of(
            LedgerId.generate(),
            List.of(section),
            new ReportSummary(1, Money.of("10.00", USD), Money.of("10.00", USD)),
            Instant.parse("2026-07-31T00:00:00Z"));

    String text = formatter.format(report);

    assertThat(text).contains("Cash:").contains("Opening: 0.00 USD").contains("lines=1");
  }
}
