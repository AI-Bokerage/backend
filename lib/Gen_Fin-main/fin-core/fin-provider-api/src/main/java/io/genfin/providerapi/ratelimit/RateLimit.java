package io.genfin.providerapi.ratelimit;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import java.time.Duration;

public record RateLimit(int permits, Duration window) implements ValueObject {

  public RateLimit {
    Validate.argument(permits > 0, "permits must be positive.");
    Validate.notNull(window, "window must not be null.");
  }
}
