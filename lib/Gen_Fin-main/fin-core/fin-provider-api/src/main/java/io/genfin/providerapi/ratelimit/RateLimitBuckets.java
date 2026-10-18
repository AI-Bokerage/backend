package io.genfin.providerapi.ratelimit;

import io.genfin.providerapi.internal.ratelimit.TokenBucketRateLimitBucket;
import io.genfin.providerapi.port.ratelimit.RateLimitBucket;

public final class RateLimitBuckets {

  private RateLimitBuckets() {}

  public static RateLimitBucket of(RateLimit limit) {
    return new TokenBucketRateLimitBucket(limit);
  }
}
