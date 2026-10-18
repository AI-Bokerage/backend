package io.genfin.payment.idempotency;

import io.genfin.api.domain.ValueObject;

public record IdempotencyResult(IdempotencyKey key, IdempotencyOutcome outcome)
    implements ValueObject {

  public boolean isNew() {
    return outcome == IdempotencyOutcome.NEW;
  }

  public boolean isReplay() {
    return outcome == IdempotencyOutcome.REPLAYED;
  }

  public boolean isConflict() {
    return outcome == IdempotencyOutcome.CONFLICT;
  }
}
