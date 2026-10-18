package io.genfin.invoice.calculation;

import static io.genfin.invoice.support.TestCurrencies.USD;
import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.api.time.ClockProviders;
import io.genfin.invoice.discount.Discount;
import io.genfin.invoice.factory.AdjustmentFactory;
import io.genfin.invoice.factory.LineFactory;
import io.genfin.invoice.invoice.Invoice;
import io.genfin.invoice.invoice.InvoiceBuilder;
import io.genfin.money.money.Money;
import io.genfin.money.percentage.Percentage;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InvoiceCalculatorTest {

  private static Invoice draft() {
    return InvoiceBuilder.newInvoice()
        .currency(USD)
        .dueDate(Instant.parse("2026-02-01T00:00:00Z"))
        .clockProvider(ClockProviders.system())
        .actor("tester")
        .build();
  }

  @Test
  void subtotalDiscountAdjustmentAndGrandTotalAreComputedCorrectly() {
    Invoice invoice = draft();
    invoice.addLine(
        LineFactory.simple("Item A", BigDecimal.ONE, Money.of("100.00", USD)),
        ClockProviders.system(),
        "t");
    invoice.addLine(
        LineFactory.simple("Item B", new BigDecimal("2"), Money.of("50.00", USD)),
        ClockProviders.system(),
        "t");
    invoice.addDiscount(
        Discount.percentage(Percentage.ofPercent(new BigDecimal("10")), "promo"),
        ClockProviders.system(),
        "t");
    invoice.addAdjustment(
        AdjustmentFactory.fee(Money.of("5.00", USD), "processing"), ClockProviders.system(), "t");

    var result =
        InvoiceCalculators.standard()
            .calculate(invoice, InvoiceCalculationContext.standard(Instant.now()));

    assertThat(result.subtotal()).isEqualTo(Money.of("200.00", USD));
    assertThat(result.discountTotal()).isEqualTo(Money.of("20.00", USD));
    assertThat(result.adjustmentTotal()).isEqualTo(Money.of("5.00", USD));
    assertThat(result.grandTotal()).isEqualTo(Money.of("185.00", USD));
    assertThat(result.balanceDue()).isEqualTo(Money.of("185.00", USD));
  }

  @Test
  void balanceDueReflectsAmountPaid() {
    Invoice invoice = draft();
    invoice.addLine(
        LineFactory.simple("Item", BigDecimal.ONE, Money.of("100.00", USD)),
        ClockProviders.system(),
        "t");
    invoice.issue(
        io.genfin.invoice.numbering.InvoiceNumber.of("INV-1"), ClockProviders.system(), "t");
    invoice.recordPayment(
        Money.of("40.00", USD), Money.of("100.00", USD), ClockProviders.system(), "t");

    var result =
        InvoiceCalculators.standard()
            .calculate(invoice, InvoiceCalculationContext.standard(Instant.now()));

    assertThat(result.balanceDue()).isEqualTo(Money.of("60.00", USD));
  }

  @Test
  void multipleHeaderDiscountsNeverCombineBelowZero() {
    Invoice invoice = draft();
    invoice.addLine(
        LineFactory.simple("Item", BigDecimal.ONE, Money.of("100.00", USD)),
        ClockProviders.system(),
        "t");
    invoice.addDiscount(
        Discount.percentage(Percentage.ofPercent(new BigDecimal("60")), "a"),
        ClockProviders.system(),
        "t");
    invoice.addDiscount(
        Discount.percentage(Percentage.ofPercent(new BigDecimal("60")), "b"),
        ClockProviders.system(),
        "t");

    var result =
        InvoiceCalculators.standard()
            .calculate(invoice, InvoiceCalculationContext.standard(Instant.now()));

    // Sequential application: 60% of 100 = 60 (remaining 40), then 60% of 40 = 24 (remaining 16) —
    // never negative.
    assertThat(result.grandTotal().isNegative()).isFalse();
    assertThat(result.discountTotal()).isEqualTo(Money.of("84.00", USD));
  }

  @Test
  void creditAdjustmentReducesGrandTotal() {
    Invoice invoice = draft();
    invoice.addLine(
        LineFactory.simple("Item", BigDecimal.ONE, Money.of("100.00", USD)),
        ClockProviders.system(),
        "t");
    invoice.addAdjustment(
        AdjustmentFactory.credit(Money.of("100.00", USD), "goodwill"),
        ClockProviders.system(),
        "t");

    var result =
        InvoiceCalculators.standard()
            .calculate(invoice, InvoiceCalculationContext.standard(Instant.now()));

    assertThat(result.grandTotal()).isEqualTo(Money.zero(USD));
  }
}
