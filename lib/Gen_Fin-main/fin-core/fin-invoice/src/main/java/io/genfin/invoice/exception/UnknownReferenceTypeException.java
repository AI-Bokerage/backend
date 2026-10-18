package io.genfin.invoice.exception;

import io.genfin.api.exception.GenFinException;

public class UnknownReferenceTypeException extends GenFinException {

  public UnknownReferenceTypeException(String code) {
    super(InvoiceErrorCode.UNKNOWN_REFERENCE_TYPE, "Unknown reference type: " + code);
  }
}
