package io.genfin.payment.exception;

import io.genfin.api.exception.GenFinException;

public class PaymentCurrencyMismatchException extends GenFinException {

  public PaymentCurrencyMismatchException(String expected, String actual) {
    super(
        PaymentErrorCode.CURRENCY_MISMATCH, "Expected currency " + expected + " but got " + actual);
  }
}
