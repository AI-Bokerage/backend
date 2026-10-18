package io.genfin.invoice.calculation;

import io.genfin.invoice.internal.calculation.DefaultInvoiceCalculator;
import io.genfin.invoice.port.calculation.InvoiceCalculator;

public final class InvoiceCalculators {

  private static final InvoiceCalculator STANDARD = new DefaultInvoiceCalculator();

  private InvoiceCalculators() {}

  public static InvoiceCalculator standard() {
    return STANDARD;
  }
}
