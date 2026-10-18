package io.genfin.stripe.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.AuthenticationException;
import io.genfin.providerapi.internal.retry.DefaultRetryPolicy;
import io.genfin.providerapi.retry.RetryPolicies;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class StripeRetryExecutorTest {

  private static final int SUCCEED_ON_ATTEMPT = 3;

  @Test
  void retriesRetryableFailuresUntilSuccess() throws Exception {
    StripeRetryExecutor executor =
        new StripeRetryExecutor(
            new DefaultRetryPolicy(SUCCEED_ON_ATTEMPT, RetryPolicies.fixed(Duration.ofMillis(1))));
    AtomicInteger attempts = new AtomicInteger();

    String result =
        executor.execute(
            () -> {
              if (attempts.incrementAndGet() < SUCCEED_ON_ATTEMPT) {
                throw new ApiConnectionException("network blip");
              }
              return "ok";
            });

    assertThat(result).isEqualTo("ok");
    assertThat(attempts.get()).isEqualTo(SUCCEED_ON_ATTEMPT);
  }

  @Test
  void doesNotRetryNonRetryableFailures() {
    StripeRetryExecutor executor =
        new StripeRetryExecutor(
            new DefaultRetryPolicy(3, RetryPolicies.fixed(Duration.ofMillis(1))));
    AtomicInteger attempts = new AtomicInteger();

    assertThatThrownBy(
            () ->
                executor.execute(
                    () -> {
                      attempts.incrementAndGet();
                      throw new AuthenticationException("bad key", null, null, null);
                    }))
        .isInstanceOf(AuthenticationException.class);
    assertThat(attempts.get()).isEqualTo(1);
  }

  @Test
  void givesUpAfterMaxAttemptsExhausted() {
    StripeRetryExecutor executor =
        new StripeRetryExecutor(
            new DefaultRetryPolicy(2, RetryPolicies.fixed(Duration.ofMillis(1))));
    AtomicInteger attempts = new AtomicInteger();

    assertThatThrownBy(
            () ->
                executor.execute(
                    () -> {
                      attempts.incrementAndGet();
                      throw new ApiConnectionException("still down");
                    }))
        .isInstanceOf(ApiConnectionException.class);
    assertThat(attempts.get()).isEqualTo(2);
  }
}
