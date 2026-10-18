package io.genfin.invoice.port.format;

import io.genfin.api.port.spi.Extension;
import io.genfin.invoice.calculation.InvoiceCalculationResult;
import io.genfin.invoice.invoice.Invoice;
import io.genfin.money.format.FormattingContext;

public interface InvoiceFormatter extends Extension {

  String format(Invoice invoice, InvoiceCalculationResult totals, FormattingContext context);
}
