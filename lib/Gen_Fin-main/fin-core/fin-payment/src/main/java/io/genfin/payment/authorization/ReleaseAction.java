package io.genfin.payment.authorization;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import java.time.Instant;

/** Releasing the un-captured remainder of a partially-captured authorization hold. */
public record ReleaseAction(Money amount, Instant releasedAt, String reason)
    implements ValueObject {

  public ReleaseAction {
    Validate.notNull(amount, "amount must not be null.");
    Validate.notNull(releasedAt, "releasedAt must not be null.");
  }
}
