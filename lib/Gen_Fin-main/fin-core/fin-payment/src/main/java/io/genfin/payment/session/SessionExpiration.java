package io.genfin.payment.session;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import java.time.Instant;

public record SessionExpiration(Instant expiresAt) implements ValueObject {

  public SessionExpiration {
    Validate.notNull(expiresAt, "expiresAt must not be null.");
  }

  public boolean isExpired(Instant asOf) {
    return asOf.isAfter(expiresAt);
  }
}
