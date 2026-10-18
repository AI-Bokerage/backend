package io.genfin.payment.internal.idempotency;

import io.genfin.payment.idempotency.IdempotencyKey;
import io.genfin.payment.idempotency.IdempotencyOutcome;
import io.genfin.payment.idempotency.IdempotencyResult;
import io.genfin.payment.port.idempotency.IdempotencyValidator;
import java.util.Optional;

public final class DefaultIdempotencyValidator implements IdempotencyValidator {

  @Override
  public IdempotencyResult validate(
      IdempotencyKey key, String requestFingerprint, Optional<String> existingFingerprint) {
    if (existingFingerprint.isEmpty()) {
      return new IdempotencyResult(key, IdempotencyOutcome.NEW);
    }
    boolean matches = existingFingerprint.get().equals(requestFingerprint);
    return new IdempotencyResult(
        key, matches ? IdempotencyOutcome.REPLAYED : IdempotencyOutcome.CONFLICT);
  }
}
