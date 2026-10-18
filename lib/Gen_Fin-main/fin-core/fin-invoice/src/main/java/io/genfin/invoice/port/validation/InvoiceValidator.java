package io.genfin.invoice.port.validation;

import io.genfin.api.port.spi.Extension;
import io.genfin.invoice.invoice.Invoice;
import io.genfin.invoice.validation.ValidationContext;
import io.genfin.invoice.validation.ValidationResult;

public interface InvoiceValidator extends Extension {

  ValidationResult validate(Invoice invoice, ValidationContext context);
}
