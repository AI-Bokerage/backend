package io.genfin.refund.attempt;

import static io.genfin.refund.support.TestCurrencies.USD;
import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.api.time.ClockProviders;
import io.genfin.money.money.Money;
import io.genfin.payment.failure.FailureCategory;
import io.genfin.payment.failure.FailureReason;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RefundAttemptTest {

  @Test
  void succeededAttemptCarriesNoFailureReasonAndIsInitial() {
    RefundAttempt attempt =
        RefundAttemptBuilder.newAttempt()
            .clockProvider(ClockProviders.fixed(Instant.parse("2026-01-01T00:00:00Z")))
            .amount(Money.of("10.00", USD))
            .result(RefundAttemptResult.SUCCEEDED)
            .gatewayReference("gw-ref-1")
            .build();

    assertThat(attempt.succeeded()).isTrue();
    assertThat(attempt.failureReason()).isEmpty();
    assertThat(attempt.gatewayReference()).contains("gw-ref-1");
    assertThat(attempt.retryReference().isRetry()).isFalse();
  }

  @Test
  void retriedAttemptReferencesThePreviousAttemptAndSequenceAppendsWithoutOverwriting() {
    RefundAttempt first =
        RefundAttemptBuilder.newAttempt()
            .clockProvider(ClockProviders.system())
            .amount(Money.of("10.00", USD))
            .result(RefundAttemptResult.FAILED)
            .failureReason(FailureReason.of(FailureCategory.PROVIDER_ERROR, "gateway timeout"))
            .build();

    RefundAttempt retry =
        RefundAttemptBuilder.newAttempt()
            .clockProvider(ClockProviders.system())
            .attemptNumber(1)
            .amount(Money.of("10.00", USD))
            .result(RefundAttemptResult.SUCCEEDED)
            .retryReference(RetryReference.retryOf(first.id()))
            .build();

    AttemptSequence sequence = AttemptSequence.empty().append(first).append(retry);

    assertThat(sequence.size()).isEqualTo(2);
    assertThat(sequence.all()).containsExactly(first, retry);
    assertThat(sequence.latest()).contains(retry);
    assertThat(retry.retryReference().previousAttempt()).contains(first.id());
    assertThat(sequence.nextAttemptNumber()).isEqualTo(2);
  }
}
