package io.genfin.payment.idempotency;

import io.genfin.payment.internal.idempotency.DefaultIdempotencyValidator;
import io.genfin.payment.port.idempotency.IdempotencyValidator;

public final class IdempotencyValidators {

  private static final IdempotencyValidator STANDARD = new DefaultIdempotencyValidator();

  private IdempotencyValidators() {}

  public static IdempotencyValidator standard() {
    return STANDARD;
  }
}
