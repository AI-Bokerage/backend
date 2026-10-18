package io.genfin.invoice.invoice;

import static io.genfin.invoice.support.TestCurrencies.USD;
import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.api.time.ClockProviders;
import io.genfin.invoice.calculation.InvoiceCalculationContext;
import io.genfin.invoice.calculation.InvoiceCalculators;
import io.genfin.invoice.factory.LineFactory;
import io.genfin.money.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LargeInvoiceTest {

  @Test
  void thousandsOfLinesSumCorrectly() {
    Invoice invoice =
        InvoiceBuilder.newInvoice()
            .currency(USD)
            .dueDate(Instant.parse("2026-02-01T00:00:00Z"))
            .clockProvider(ClockProviders.system())
            .actor("tester")
            .build();

    int lineCount = 5000;
    for (int i = 0; i < lineCount; i++) {
      invoice.addLine(
          LineFactory.simple("Item " + i, BigDecimal.ONE, Money.of("1.11", USD)),
          ClockProviders.system(),
          "tester");
    }

    var result =
        InvoiceCalculators.standard()
            .calculate(invoice, InvoiceCalculationContext.standard(Instant.now()));

    assertThat(invoice.lines()).hasSize(lineCount);
    assertThat(result.subtotal())
        .isEqualTo(Money.of("1.11", USD).multiply(BigDecimal.valueOf(lineCount)));
  }
}
