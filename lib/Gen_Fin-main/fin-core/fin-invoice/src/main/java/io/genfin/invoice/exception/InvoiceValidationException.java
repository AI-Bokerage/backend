package io.genfin.invoice.exception;

import io.genfin.api.exception.GenFinException;
import java.util.Map;

public class InvoiceValidationException extends GenFinException {

  public InvoiceValidationException(String message, Map<String, Object> metadata) {
    super(InvoiceErrorCode.VALIDATION_FAILED, message, null, metadata);
  }
}
