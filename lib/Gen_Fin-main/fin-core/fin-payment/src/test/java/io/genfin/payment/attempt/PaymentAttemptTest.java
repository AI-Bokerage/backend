package io.genfin.payment.attempt;

import static io.genfin.payment.support.TestCurrencies.USD;
import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.api.time.ClockProviders;
import io.genfin.money.money.Money;
import io.genfin.payment.failure.FailureCategory;
import io.genfin.payment.failure.FailureReason;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PaymentAttemptTest {

  @Test
  void succeededAttemptCarriesNoFailureReason() {
    PaymentAttempt attempt =
        AttemptBuilder.newAttempt()
            .clockProvider(ClockProviders.fixed(Instant.parse("2026-01-01T00:00:00Z")))
            .amount(Money.of("10.00", USD))
            .result(AttemptResult.SUCCEEDED)
            .gatewayReference("gw-ref-1")
            .build();

    assertThat(attempt.succeeded()).isTrue();
    assertThat(attempt.failureReason()).isEmpty();
    assertThat(attempt.gatewayReference()).contains("gw-ref-1");
  }

  @Test
  void failedAttemptCarriesAFailureReason() {
    PaymentAttempt attempt =
        AttemptBuilder.newAttempt()
            .clockProvider(ClockProviders.system())
            .retryNumber(1)
            .amount(Money.of("10.00", USD))
            .result(AttemptResult.FAILED)
            .failureReason(FailureReason.of(FailureCategory.CARD_DECLINED, "declined"))
            .build();

    assertThat(attempt.succeeded()).isFalse();
    assertThat(attempt.retryNumber()).isEqualTo(1);
    assertThat(attempt.failureReason()).isPresent();
  }
}
