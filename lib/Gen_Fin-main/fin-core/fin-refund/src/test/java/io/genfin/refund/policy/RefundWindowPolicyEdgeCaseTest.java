package io.genfin.refund.policy;

import static io.genfin.refund.support.TestCurrencies.USD;
import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.money.money.Money;
import io.genfin.refund.internal.policy.DefaultRefundWindowPolicy;
import io.genfin.refund.port.policy.RefundWindowPolicy;
import io.genfin.refund.reason.StandardRefundReason;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Edge cases at the exact boundary of a refund window, one place where off-by-one bugs hide. */
class RefundWindowPolicyEdgeCaseTest {

  private static final Instant PAID_AT = Instant.parse("2026-01-01T00:00:00Z");
  private static final Duration WINDOW = Duration.ofDays(30);

  private static RefundPolicyContext contextAt(Instant evaluationTime) {
    return new RefundPolicyContext(
        Money.of("100.00", USD),
        List.of(),
        Money.of("10.00", USD),
        StandardRefundReason.CUSTOMER_REQUEST,
        PAID_AT,
        evaluationTime);
  }

  @Test
  void requestExactlyAtTheWindowBoundaryIsStillPermitted() {
    RefundWindowPolicy policy = RefundPolicies.windowPolicy(WINDOW);

    assertThat(policy.check(contextAt(PAID_AT.plus(WINDOW)))).isEmpty();
  }

  @Test
  void requestOneNanosecondPastTheWindowBoundaryIsRejected() {
    RefundWindowPolicy policy = RefundPolicies.windowPolicy(WINDOW);

    assertThat(policy.check(contextAt(PAID_AT.plus(WINDOW).plusNanos(1)))).isPresent();
  }

  @Test
  void requestAtTheMomentOfPaymentIsPermitted() {
    RefundWindowPolicy policy = RefundPolicies.windowPolicy(WINDOW);

    assertThat(policy.check(contextAt(PAID_AT))).isEmpty();
  }

  @Test
  void aZeroLengthWindowOnlyPermitsTheExactPaymentInstant() {
    RefundWindowPolicy policy = new DefaultRefundWindowPolicy(Duration.ZERO);

    assertThat(policy.check(contextAt(PAID_AT))).isEmpty();
    assertThat(policy.check(contextAt(PAID_AT.plusNanos(1)))).isPresent();
  }

  @Test
  void anEvaluationTimeBeforeThePaymentDateIsStillWithinWindow() {
    RefundWindowPolicy policy = RefundPolicies.windowPolicy(WINDOW);

    assertThat(policy.check(contextAt(PAID_AT.minus(Duration.ofDays(1))))).isEmpty();
  }
}
