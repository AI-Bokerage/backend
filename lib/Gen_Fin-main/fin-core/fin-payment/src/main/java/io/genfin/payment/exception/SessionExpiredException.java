package io.genfin.payment.exception;

import io.genfin.api.exception.GenFinException;

public class SessionExpiredException extends GenFinException {

  public SessionExpiredException(String sessionId) {
    super(PaymentErrorCode.SESSION_EXPIRED, "Payment session has expired: " + sessionId);
  }
}
