package io.genfin.invoice.line;

import static io.genfin.invoice.support.TestCurrencies.USD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.invoice.discount.Discount;
import io.genfin.money.money.Money;
import io.genfin.money.percentage.Percentage;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class InvoiceLineTest {

  @Test
  void netAmountAppliesDiscount() {
    InvoiceLine line =
        InvoiceLineBuilder.newLine()
            .description("Consulting")
            .quantity(new BigDecimal("2"))
            .unitPrice(Money.of("100.00", USD))
            .discount(Discount.percentage(Percentage.ofPercent(new BigDecimal("10")), "bulk"))
            .build();

    assertThat(line.grossListAmount()).isEqualTo(Money.of("200.00", USD));
    assertThat(line.discountAmount()).isEqualTo(Money.of("20.00", USD));
    assertThat(line.netAmount()).isEqualTo(Money.of("180.00", USD));
  }

  @Test
  void lineWithoutDiscountNetsToGrossAmount() {
    InvoiceLine line =
        InvoiceLineBuilder.newLine()
            .description("Widget")
            .quantity(BigDecimal.ONE)
            .unitPrice(Money.of("50.00", USD))
            .build();

    assertThat(line.netAmount()).isEqualTo(line.grossListAmount());
  }

  @Test
  void zeroOrNegativeQuantityIsRejected() {
    assertThatThrownBy(
            () ->
                InvoiceLineBuilder.newLine()
                    .description("Bad")
                    .quantity(BigDecimal.ZERO)
                    .unitPrice(Money.of("1.00", USD))
                    .build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void negativeUnitPriceIsRejected() {
    assertThatThrownBy(
            () ->
                InvoiceLineBuilder.newLine()
                    .description("Bad")
                    .quantity(BigDecimal.ONE)
                    .unitPrice(Money.of("-1.00", USD))
                    .build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void eachLineGetsAFreshIdentityByDefault() {
    InvoiceLine a =
        InvoiceLineBuilder.newLine()
            .description("A")
            .quantity(BigDecimal.ONE)
            .unitPrice(Money.of("1", USD))
            .build();
    InvoiceLine b =
        InvoiceLineBuilder.newLine()
            .description("A")
            .quantity(BigDecimal.ONE)
            .unitPrice(Money.of("1", USD))
            .build();

    assertThat(a.id()).isNotEqualTo(b.id());
    assertThat(a).isNotEqualTo(b);
  }
}
