package io.genfin.invoice.port.calculation;

import io.genfin.api.port.spi.Extension;
import io.genfin.invoice.calculation.InvoiceCalculationContext;
import io.genfin.invoice.calculation.InvoiceCalculationResult;
import io.genfin.invoice.invoice.Invoice;

/**
 * Computes every total for an {@link Invoice}. {@code Invoice} itself never performs this
 * calculation.
 */
public interface InvoiceCalculator extends Extension {

  InvoiceCalculationResult calculate(Invoice invoice, InvoiceCalculationContext context);
}
