package io.genfin.payment.exception;

import io.genfin.api.exception.GenFinException;
import java.util.Map;

public class PaymentValidationException extends GenFinException {

  public PaymentValidationException(String message, Map<String, Object> metadata) {
    super(PaymentErrorCode.VALIDATION_FAILED, message, null, metadata);
  }
}
