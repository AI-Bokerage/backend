package io.genfin.invoice.numbering;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;

/**
 * The human-facing invoice number — its shape is entirely up to the configured {@code
 * InvoiceNumberGenerator}.
 */
public record InvoiceNumber(String value) implements ValueObject {

  public InvoiceNumber {
    Validate.notBlank(value, "value must not be blank.");
  }

  public static InvoiceNumber of(String value) {
    return new InvoiceNumber(value);
  }
}
