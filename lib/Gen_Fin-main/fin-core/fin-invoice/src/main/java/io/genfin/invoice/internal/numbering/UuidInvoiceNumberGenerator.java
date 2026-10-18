package io.genfin.invoice.internal.numbering;

import io.genfin.api.id.IdentifierGenerators;
import io.genfin.invoice.numbering.InvoiceNumber;
import io.genfin.invoice.numbering.NumberGenerationContext;
import io.genfin.invoice.port.numbering.InvoiceNumberGenerator;

public final class UuidInvoiceNumberGenerator implements InvoiceNumberGenerator {

  @Override
  public InvoiceNumber generate(NumberGenerationContext context) {
    return InvoiceNumber.of(IdentifierGenerators.uuidV4().generate());
  }
}
