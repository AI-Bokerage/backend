package io.genfin.invoice.port.numbering;

import io.genfin.api.port.spi.Extension;
import io.genfin.invoice.numbering.InvoiceNumber;
import io.genfin.invoice.numbering.NumberGenerationContext;

/**
 * Generates the next {@link InvoiceNumber}. Implement this for sequential, timestamp, UUID, ERP, or
 * region-specific schemes.
 */
public interface InvoiceNumberGenerator extends Extension {

  InvoiceNumber generate(NumberGenerationContext context);
}
