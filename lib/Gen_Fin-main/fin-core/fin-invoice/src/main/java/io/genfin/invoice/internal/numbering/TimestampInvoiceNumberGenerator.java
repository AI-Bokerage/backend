package io.genfin.invoice.internal.numbering;

import io.genfin.invoice.numbering.InvoiceNumber;
import io.genfin.invoice.numbering.NumberGenerationContext;
import io.genfin.invoice.port.numbering.InvoiceNumberGenerator;
import java.time.format.DateTimeFormatter;

public final class TimestampInvoiceNumberGenerator implements InvoiceNumberGenerator {

  private static final DateTimeFormatter FORMAT =
      DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").withZone(java.time.ZoneOffset.UTC);

  @Override
  public InvoiceNumber generate(NumberGenerationContext context) {
    return InvoiceNumber.of("INV-" + FORMAT.format(context.asOf()));
  }
}
