package io.genfin.invoice.exception;

import io.genfin.api.exception.GenFinException;

public class IllegalInvoiceStateTransitionException extends GenFinException {

  public IllegalInvoiceStateTransitionException(String message) {
    super(InvoiceErrorCode.ILLEGAL_STATE_TRANSITION, message);
  }
}
