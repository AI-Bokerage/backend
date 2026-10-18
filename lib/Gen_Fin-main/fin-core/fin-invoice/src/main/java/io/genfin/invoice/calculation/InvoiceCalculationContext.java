package io.genfin.invoice.calculation;

import java.time.Instant;

public record InvoiceCalculationContext(Instant asOf, InvoiceCalculationPolicy policy) {

  public static InvoiceCalculationContext standard(Instant asOf) {
    return new InvoiceCalculationContext(asOf, InvoiceCalculationPolicy.standard());
  }
}
