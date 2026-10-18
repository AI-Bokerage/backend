package io.genfin.payment.exception;

import io.genfin.api.exception.GenFinException;

public class DuplicateIdempotencyKeyException extends GenFinException {

  public DuplicateIdempotencyKeyException(String key) {
    super(
        PaymentErrorCode.DUPLICATE_IDEMPOTENCY_KEY,
        "Idempotency key already used with a different request: " + key);
  }
}
