package io.genfin.providerapi.internal.ratelimit;

import io.genfin.providerapi.port.ratelimit.RateLimitBucket;
import io.genfin.providerapi.ratelimit.RateLimit;
import io.genfin.providerapi.ratelimit.ThrottleDecision;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** A simple fixed-window token bucket — refills fully at the start of each window. */
public final class TokenBucketRateLimitBucket implements RateLimitBucket {

  private final RateLimit limit;
  private final AtomicReference<Instant> windowStart;
  private final AtomicInteger remaining;

  public TokenBucketRateLimitBucket(RateLimit limit) {
    this.limit = limit;
    this.windowStart = new AtomicReference<>(Instant.now());
    this.remaining = new AtomicInteger(limit.permits());
  }

  @Override
  public synchronized ThrottleDecision tryConsume() {
    Instant now = Instant.now();
    if (now.isAfter(windowStart.get().plus(limit.window()))) {
      windowStart.set(now);
      remaining.set(limit.permits());
    }
    if (remaining.get() <= 0) {
      Duration retryAfter = Duration.between(now, windowStart.get().plus(limit.window()));
      return ThrottleDecision.reject(retryAfter.isNegative() ? Duration.ZERO : retryAfter);
    }
    remaining.decrementAndGet();
    return ThrottleDecision.allow();
  }
}
