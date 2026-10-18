package io.genfin.invoice.internal.calculation;

import io.genfin.invoice.adjustment.Adjustment;
import io.genfin.invoice.calculation.InvoiceCalculationContext;
import io.genfin.invoice.calculation.InvoiceCalculationResult;
import io.genfin.invoice.discount.Discount;
import io.genfin.invoice.invoice.Invoice;
import io.genfin.invoice.line.InvoiceLine;
import io.genfin.invoice.port.calculation.InvoiceCalculator;
import io.genfin.money.money.Money;
import io.genfin.money.tax.TaxBreakdown;

/**
 * Sums line net amounts, applies header-level discounts sequentially (so multiple discounts never
 * combine to exceed the subtotal), folds in adjustments, and totals line-level tax.
 */
public final class DefaultInvoiceCalculator implements InvoiceCalculator {

  @Override
  public InvoiceCalculationResult calculate(Invoice invoice, InvoiceCalculationContext context) {
    Money currency0 = Money.zero(invoice.currency());

    Money lineSubtotal = currency0;
    Money taxTotal = currency0;
    for (InvoiceLine line : invoice.lines()) {
      lineSubtotal = lineSubtotal.add(line.netAmount(context.policy().discountEngine()));
      taxTotal = taxTotal.add(line.taxBreakdown().totalTax());
    }

    Money remaining = lineSubtotal;
    Money headerDiscountTotal = currency0;
    for (Discount discount : invoice.discounts()) {
      Money amount = context.policy().discountEngine().apply(discount, remaining);
      headerDiscountTotal = headerDiscountTotal.add(amount);
      remaining = remaining.subtract(amount);
    }

    Money adjustmentTotal = currency0;
    for (Adjustment adjustment : invoice.adjustments()) {
      adjustmentTotal = adjustmentTotal.add(adjustment.amount());
    }

    Money grandTotal =
        lineSubtotal.subtract(headerDiscountTotal).add(adjustmentTotal).add(taxTotal);
    Money balanceDue = grandTotal.subtract(invoice.amountPaid());

    return new InvoiceCalculationResult(
        lineSubtotal,
        headerDiscountTotal,
        adjustmentTotal,
        lineTaxBreakdown(invoice),
        grandTotal,
        invoice.amountPaid(),
        balanceDue);
  }

  private static TaxBreakdown lineTaxBreakdown(Invoice invoice) {
    var components =
        invoice.lines().stream()
            .flatMap(line -> line.taxBreakdown().components().stream())
            .toList();
    return new TaxBreakdown(components, invoice.currency());
  }
}
