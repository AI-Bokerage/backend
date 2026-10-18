package io.genfin.providerapi.port.ratelimit;

import io.genfin.providerapi.ratelimit.ThrottleDecision;

/** One rate-limit bucket instance (per provider/key) applications query before making a call. */
public interface RateLimitBucket {

  ThrottleDecision tryConsume();
}
