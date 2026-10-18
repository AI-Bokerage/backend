package io.genfin.payment.exception;

import io.genfin.api.exception.GenFinException;

public class CaptureExceedsAuthorizationException extends GenFinException {

  public CaptureExceedsAuthorizationException(String message) {
    super(PaymentErrorCode.CAPTURE_EXCEEDS_AUTHORIZATION, message);
  }
}
