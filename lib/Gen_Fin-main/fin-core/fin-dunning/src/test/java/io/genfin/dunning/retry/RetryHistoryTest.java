package io.genfin.dunning.retry;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.dunning.id.RetryId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RetryHistoryTest {

  @Test
  void appendingNeverMutatesTheOriginalHistory() {
    RetryHistory empty = RetryHistory.empty();
    RetryExecution first = executionAttempt(1);

    RetryHistory withOne = empty.append(first);

    assertThat(empty.isEmpty()).isTrue();
    assertThat(withOne.all()).containsExactly(first);
    assertThat(withOne.nextAttemptNumber()).isEqualTo(2);
  }

  @Test
  void latestReturnsTheMostRecentlyAppendedExecution() {
    RetryExecution first = executionAttempt(1);
    RetryExecution second = executionAttempt(2);

    RetryHistory history = RetryHistory.empty().append(first).append(second);

    assertThat(history.latest()).hasValue(second);
    assertThat(history.size()).isEqualTo(2);
  }

  private static RetryExecution executionAttempt(int attemptNumber) {
    Instant now = Instant.now();
    return new RetryExecution(
        RetryId.generate(), attemptNumber, now, now, RetryResult.FAILED, null);
  }
}
