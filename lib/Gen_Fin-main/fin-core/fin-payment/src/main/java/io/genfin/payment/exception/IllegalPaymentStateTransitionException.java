package io.genfin.payment.exception;

import io.genfin.api.exception.GenFinException;

public class IllegalPaymentStateTransitionException extends GenFinException {

  public IllegalPaymentStateTransitionException(String message) {
    super(PaymentErrorCode.ILLEGAL_STATE_TRANSITION, message);
  }
}
