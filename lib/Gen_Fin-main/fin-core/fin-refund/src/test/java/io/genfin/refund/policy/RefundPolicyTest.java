package io.genfin.refund.policy;

import static io.genfin.refund.support.TestCurrencies.USD;
import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.money.money.Money;
import io.genfin.refund.exception.RefundErrorCode;
import io.genfin.refund.port.policy.RefundPolicy;
import io.genfin.refund.reason.RefundReasonRegistries;
import io.genfin.refund.reason.StandardRefundReason;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RefundPolicyTest {

  private static final Instant PAID_AT = Instant.parse("2026-01-01T00:00:00Z");

  private static RefundPolicyContext context(
      Money paymentTotal, List<Money> priorRefunds, Money requestedAmount, Instant evaluationTime) {
    return new RefundPolicyContext(
        paymentTotal,
        priorRefunds,
        requestedAmount,
        StandardRefundReason.CUSTOMER_REQUEST,
        PAID_AT,
        evaluationTime);
  }

  private static RefundPolicy standardPolicy() {
    return RefundPolicies.standard(
        RefundReasonRegistries.withProvider(RefundReasonRegistries.standardCatalog()));
  }

  @Test
  void permitsAnInWindowPartialRefundWithAnAllowedReason() {
    RefundPolicyDecision decision =
        standardPolicy()
            .evaluate(
                context(
                    Money.of("100.00", USD),
                    List.of(),
                    Money.of("40.00", USD),
                    PAID_AT.plus(Duration.ofDays(10))));

    assertThat(decision.isPermitted()).isTrue();
    assertThat(decision.approvalRequired()).isFalse();
  }

  @Test
  void rejectsARefundRequestedAfterTheWindowElapsed() {
    RefundPolicyDecision decision =
        standardPolicy()
            .evaluate(
                context(
                    Money.of("100.00", USD),
                    List.of(),
                    Money.of("40.00", USD),
                    PAID_AT.plus(Duration.ofDays(200))));

    assertThat(decision.isPermitted()).isFalse();
    assertThat(decision.violations())
        .extracting(PolicyViolation::code)
        .containsExactly(RefundErrorCode.REFUND_WINDOW_EXPIRED);
  }

  @Test
  void rejectsARefundThatWouldExceedAConfiguredMaximum() {
    RefundPolicy policy =
        new io.genfin.refund.internal.policy.DefaultRefundPolicy(
            RefundPolicies.defaultWindowPolicy(),
            RefundPolicies.maximumRefundPolicy(Money.of("50.00", USD)),
            RefundPolicies.reasonPolicy(
                RefundReasonRegistries.withProvider(RefundReasonRegistries.standardCatalog())),
            RefundPolicies.defaultApprovalPolicy());

    RefundPolicyDecision decision =
        policy.evaluate(
            context(
                Money.of("100.00", USD),
                List.of(Money.of("30.00", USD)),
                Money.of("30.00", USD),
                PAID_AT.plus(Duration.ofDays(1))));

    assertThat(decision.isPermitted()).isFalse();
    assertThat(decision.violations())
        .extracting(PolicyViolation::code)
        .containsExactly(RefundErrorCode.REFUND_EXCEEDS_REFUNDABLE_BALANCE);
  }

  @Test
  void rejectsAReasonNotRegisteredInTheCatalog() {
    RefundPolicyDecision decision =
        standardPolicy()
            .evaluate(
                new RefundPolicyContext(
                    Money.of("100.00", USD),
                    List.of(),
                    Money.of("10.00", USD),
                    () -> "UNREGISTERED_REASON",
                    PAID_AT,
                    PAID_AT.plus(Duration.ofDays(1))));

    assertThat(decision.violations())
        .extracting(PolicyViolation::code)
        .containsExactly(RefundErrorCode.INVALID_REFUND_REASON);
  }

  @Test
  void requiresApprovalOnceAConfiguredThresholdIsExceeded() {
    RefundPolicy policy =
        new io.genfin.refund.internal.policy.DefaultRefundPolicy(
            RefundPolicies.defaultWindowPolicy(),
            RefundPolicies.defaultMaximumRefundPolicy(),
            RefundPolicies.reasonPolicy(
                RefundReasonRegistries.withProvider(RefundReasonRegistries.standardCatalog())),
            RefundPolicies.approvalPolicy(Money.of("20.00", USD)));

    RefundPolicyDecision decision =
        policy.evaluate(
            context(
                Money.of("100.00", USD),
                List.of(),
                Money.of("50.00", USD),
                PAID_AT.plus(Duration.ofDays(1))));

    assertThat(decision.approvalRequired()).isTrue();
  }
}
