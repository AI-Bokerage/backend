package io.genfin.payment.idempotency;

public enum IdempotencyOutcome {
  NEW,
  REPLAYED,
  CONFLICT
}
