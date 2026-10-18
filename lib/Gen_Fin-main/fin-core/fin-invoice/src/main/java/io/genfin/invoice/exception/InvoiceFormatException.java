package io.genfin.invoice.exception;

import io.genfin.api.exception.GenFinException;

public class InvoiceFormatException extends GenFinException {

  public InvoiceFormatException(String message, Throwable cause) {
    super(InvoiceErrorCode.INVALID_INVOICE_FORMAT, message, cause);
  }
}
