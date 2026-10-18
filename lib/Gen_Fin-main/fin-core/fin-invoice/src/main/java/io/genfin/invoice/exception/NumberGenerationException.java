package io.genfin.invoice.exception;

import io.genfin.api.exception.GenFinException;

public class NumberGenerationException extends GenFinException {

  public NumberGenerationException(String message, Throwable cause) {
    super(InvoiceErrorCode.NUMBER_GENERATION_FAILED, message, cause);
  }
}
