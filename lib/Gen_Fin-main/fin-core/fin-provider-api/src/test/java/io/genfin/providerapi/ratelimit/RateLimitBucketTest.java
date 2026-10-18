package io.genfin.providerapi.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.providerapi.port.ratelimit.RateLimitBucket;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RateLimitBucketTest {

  @Test
  void allowsUpToThePermittedCountThenRejects() {
    RateLimitBucket bucket = RateLimitBuckets.of(new RateLimit(2, Duration.ofMinutes(1)));

    assertThat(bucket.tryConsume().allowed()).isTrue();
    assertThat(bucket.tryConsume().allowed()).isTrue();
    assertThat(bucket.tryConsume().allowed()).isFalse();
  }
}
