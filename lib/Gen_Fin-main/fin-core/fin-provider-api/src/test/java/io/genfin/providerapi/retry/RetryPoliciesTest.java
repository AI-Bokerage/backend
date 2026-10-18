package io.genfin.providerapi.retry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RetryPoliciesTest {

  @Test
  void nonRetryableClassificationNeverRetries() {
    var decision = RetryPolicies.standard().decide(1, RetryClassification.NON_RETRYABLE);

    assertThat(decision.shouldRetry()).isFalse();
  }

  @Test
  void retryableClassificationRetriesUntilMaxAttempts() {
    var policy = RetryPolicies.standard();

    assertThat(policy.decide(1, RetryClassification.RETRYABLE).shouldRetry()).isTrue();
    assertThat(policy.decide(2, RetryClassification.RETRYABLE).shouldRetry()).isTrue();
    assertThat(policy.decide(3, RetryClassification.RETRYABLE).shouldRetry()).isFalse();
  }

  @Test
  void exponentialBackoffGrowsAndCaps() {
    var backoff = RetryPolicies.exponential(Duration.ofMillis(100), Duration.ofMillis(500));

    assertThat(backoff.nextDelay(1)).isEqualTo(Duration.ofMillis(100));
    assertThat(backoff.nextDelay(2)).isEqualTo(Duration.ofMillis(200));
    assertThat(backoff.nextDelay(3)).isEqualTo(Duration.ofMillis(400));
    assertThat(backoff.nextDelay(10)).isEqualTo(Duration.ofMillis(500));
  }

  @Test
  void fixedBackoffAlwaysReturnsSameDelay() {
    var backoff = RetryPolicies.fixed(Duration.ofSeconds(1));

    assertThat(backoff.nextDelay(1)).isEqualTo(Duration.ofSeconds(1));
    assertThat(backoff.nextDelay(5)).isEqualTo(Duration.ofSeconds(1));
  }
}
