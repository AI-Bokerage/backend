package io.genfin.providerapi.ratelimit;

public record RateLimitPolicy(RateLimit limit, boolean rejectOnExceed) {

  public static RateLimitPolicy reject(RateLimit limit) {
    return new RateLimitPolicy(limit, true);
  }
}
