package io.genfin.payment.authorization;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import java.time.Instant;

public record Capture(Money amount, Instant capturedAt, String gatewayReference, boolean partial)
    implements ValueObject {

  public Capture {
    Validate.notNull(amount, "amount must not be null.");
    Validate.notNull(capturedAt, "capturedAt must not be null.");
  }
}
