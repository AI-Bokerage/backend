package io.genfin.invoice.internal.format;

import io.genfin.invoice.calculation.InvoiceCalculationResult;
import io.genfin.invoice.invoice.Invoice;
import io.genfin.invoice.port.format.InvoiceFormatter;
import io.genfin.money.format.FormattingContext;
import io.genfin.money.format.MoneyFormatters;

public final class DefaultInvoiceFormatter implements InvoiceFormatter {

  @Override
  public String format(
      Invoice invoice, InvoiceCalculationResult totals, FormattingContext context) {
    String number = invoice.number().map(n -> n.value()).orElse("(unnumbered)");
    String grandTotal = MoneyFormatters.standard().format(totals.grandTotal(), context);
    String balanceDue = MoneyFormatters.standard().format(totals.balanceDue(), context);
    return "Invoice "
        + number
        + " ["
        + invoice.status().code()
        + "] Total: "
        + grandTotal
        + " Due: "
        + balanceDue;
  }
}
