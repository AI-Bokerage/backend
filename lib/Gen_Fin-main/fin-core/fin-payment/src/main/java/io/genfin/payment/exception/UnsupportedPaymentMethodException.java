package io.genfin.payment.exception;

import io.genfin.api.exception.GenFinException;

public class UnsupportedPaymentMethodException extends GenFinException {

  public UnsupportedPaymentMethodException(String methodCode) {
    super(
        PaymentErrorCode.UNSUPPORTED_PAYMENT_METHOD,
        "Unsupported or disabled payment method: " + methodCode);
  }
}
