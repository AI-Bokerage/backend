package io.genfin.payment.idempotency;

import java.time.Duration;

/**
 * How long an idempotency key remains valid for reuse. Storage/expiry enforcement is the
 * application's job.
 */
public record IdempotencyPolicy(Duration validityWindow) {

  public static IdempotencyPolicy standard() {
    return new IdempotencyPolicy(Duration.ofHours(24));
  }
}
