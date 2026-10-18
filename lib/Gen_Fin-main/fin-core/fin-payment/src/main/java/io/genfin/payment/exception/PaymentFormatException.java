package io.genfin.payment.exception;

import io.genfin.api.exception.GenFinException;

public class PaymentFormatException extends GenFinException {

  public PaymentFormatException(String message, Throwable cause) {
    super(PaymentErrorCode.INVALID_PAYMENT_FORMAT, message, cause);
  }
}
