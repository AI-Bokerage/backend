package io.genfin.razorpay.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.razorpay.RazorpayException;
import io.genfin.providerapi.internal.retry.DefaultRetryPolicy;
import io.genfin.providerapi.retry.RetryPolicies;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RazorpayRetryExecutorTest {

  private static final int SUCCEED_ON_ATTEMPT = 3;
  private static final int SUCCEED_ON_SECOND_ATTEMPT = 2;

  @Test
  void retriesRetryableFailuresUntilSuccess() throws Exception {
    RazorpayRetryExecutor executor =
        new RazorpayRetryExecutor(
            new DefaultRetryPolicy(SUCCEED_ON_ATTEMPT, RetryPolicies.fixed(Duration.ofMillis(1))));
    AtomicInteger attempts = new AtomicInteger();

    String result =
        executor.execute(
            () -> {
              if (attempts.incrementAndGet() < SUCCEED_ON_ATTEMPT) {
                // statusCode 0 mirrors how ApiUtils wraps a raw IOException.
                throw new RazorpayException("network blip");
              }
              return "ok";
            });

    assertThat(result).isEqualTo("ok");
    assertThat(attempts.get()).isEqualTo(SUCCEED_ON_ATTEMPT);
  }

  @Test
  void doesNotRetryNonRetryableFailures() {
    RazorpayRetryExecutor executor =
        new RazorpayRetryExecutor(
            new DefaultRetryPolicy(3, RetryPolicies.fixed(Duration.ofMillis(1))));
    AtomicInteger attempts = new AtomicInteger();

    assertThatThrownBy(
            () ->
                executor.execute(
                    () -> {
                      attempts.incrementAndGet();
                      throw new RazorpayException("bad key", 401);
                    }))
        .isInstanceOf(RazorpayException.class);
    assertThat(attempts.get()).isEqualTo(1);
  }

  @Test
  void givesUpAfterMaxAttemptsExhausted() {
    RazorpayRetryExecutor executor =
        new RazorpayRetryExecutor(
            new DefaultRetryPolicy(2, RetryPolicies.fixed(Duration.ofMillis(1))));
    AtomicInteger attempts = new AtomicInteger();

    assertThatThrownBy(
            () ->
                executor.execute(
                    () -> {
                      attempts.incrementAndGet();
                      throw new RazorpayException("still down");
                    }))
        .isInstanceOf(RazorpayException.class);
    assertThat(attempts.get()).isEqualTo(2);
  }

  @Test
  void classifiesRateLimitedFailuresAsRetryable() throws Exception {
    RazorpayRetryExecutor executor =
        new RazorpayRetryExecutor(
            new DefaultRetryPolicy(
                SUCCEED_ON_SECOND_ATTEMPT, RetryPolicies.fixed(Duration.ofMillis(1))));
    AtomicInteger attempts = new AtomicInteger();

    String result =
        executor.execute(
            () -> {
              if (attempts.incrementAndGet() < SUCCEED_ON_SECOND_ATTEMPT) {
                throw new RazorpayException("rate limited", 429);
              }
              return "ok";
            });

    assertThat(result).isEqualTo("ok");
    assertThat(attempts.get()).isEqualTo(SUCCEED_ON_SECOND_ATTEMPT);
  }
}
