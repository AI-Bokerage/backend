package io.genfin.invoice.exception;

import io.genfin.api.exception.GenFinException;

public class InvoiceCurrencyMismatchException extends GenFinException {

  public InvoiceCurrencyMismatchException(String expected, String actual) {
    super(
        InvoiceErrorCode.CURRENCY_MISMATCH, "Expected currency " + expected + " but got " + actual);
  }
}
