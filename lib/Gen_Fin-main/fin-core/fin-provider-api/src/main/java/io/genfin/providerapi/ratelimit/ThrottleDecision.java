package io.genfin.providerapi.ratelimit;

import io.genfin.api.domain.ValueObject;
import java.time.Duration;

public record ThrottleDecision(boolean allowed, Duration retryAfter) implements ValueObject {

  public static ThrottleDecision allow() {
    return new ThrottleDecision(true, Duration.ZERO);
  }

  public static ThrottleDecision reject(Duration retryAfter) {
    return new ThrottleDecision(false, retryAfter);
  }
}
