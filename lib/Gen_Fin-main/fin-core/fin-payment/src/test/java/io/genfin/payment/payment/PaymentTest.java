package io.genfin.payment.payment;

import static io.genfin.payment.support.TestCurrencies.USD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.port.time.ClockProvider;
import io.genfin.api.time.ClockProviders;
import io.genfin.money.money.Money;
import io.genfin.payment.event.PaymentAuthorized;
import io.genfin.payment.event.PaymentCancelled;
import io.genfin.payment.event.PaymentCaptured;
import io.genfin.payment.event.PaymentCreated;
import io.genfin.payment.event.PaymentFailed;
import io.genfin.payment.event.PaymentRefunded;
import io.genfin.payment.event.PaymentSettled;
import io.genfin.payment.exception.CaptureExceedsAuthorizationException;
import io.genfin.payment.exception.IllegalPaymentStateTransitionException;
import io.genfin.payment.failure.FailureCategory;
import io.genfin.payment.failure.FailureReason;
import io.genfin.payment.lifecycle.StandardPaymentState;
import io.genfin.payment.method.MethodBuilder;
import io.genfin.payment.method.StandardPaymentMethodType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaymentTest {

  private static final ClockProvider CLOCK =
      ClockProviders.fixed(Instant.parse("2026-01-01T00:00:00Z"));

  private static Payment newPayment() {
    return PaymentBuilder.newPayment()
        .amount(Money.of("100.00", USD))
        .method(
            MethodBuilder.newMethod()
                .type(StandardPaymentMethodType.CARD)
                .maskedIdentifier("**** 4242")
                .build())
        .clockProvider(CLOCK)
        .actor("tester")
        .build();
  }

  @Test
  void newPaymentStartsCreatedAndRecordsCreatedEvent() {
    Payment payment = newPayment();

    assertThat(payment.status()).isEqualTo(StandardPaymentState.CREATED);
    assertThat(payment.pullEvents()).hasSize(1).first().isInstanceOf(PaymentCreated.class);
  }

  @Test
  void fullAuthorizationThenFullCaptureThenSettle() {
    Payment payment = newPayment();
    payment.pullEvents();
    payment.submit(CLOCK, "t");

    payment.authorize(Money.of("100.00", USD), "auth-ref", CLOCK, "t");
    assertThat(payment.status()).isEqualTo(StandardPaymentState.AUTHORIZED);
    assertThat(payment.pullEvents()).anyMatch(PaymentAuthorized.class::isInstance);

    payment.capture(Money.of("100.00", USD), "cap-ref", CLOCK, "t");
    assertThat(payment.status()).isEqualTo(StandardPaymentState.CAPTURED);
    assertThat(payment.pullEvents()).anyMatch(PaymentCaptured.class::isInstance);

    payment.settle(CLOCK, "t");
    assertThat(payment.status()).isEqualTo(StandardPaymentState.SETTLED);
    assertThat(payment.pullEvents()).anyMatch(PaymentSettled.class::isInstance);
  }

  @Test
  void partialAuthorizationThenPartialCaptureIsTracked() {
    Payment payment = newPayment();
    payment.submit(CLOCK, "t");

    payment.authorize(Money.of("60.00", USD), "auth-ref", CLOCK, "t");
    assertThat(payment.status()).isEqualTo(StandardPaymentState.PARTIALLY_AUTHORIZED);

    payment.capture(Money.of("60.00", USD), "cap-ref", CLOCK, "t");
    assertThat(payment.status()).isEqualTo(StandardPaymentState.PARTIALLY_CAPTURED);
    assertThat(payment.totalCaptured()).isEqualTo(Money.of("60.00", USD));
  }

  @Test
  void captureExceedingAuthorizationThrows() {
    Payment payment = newPayment();
    payment.submit(CLOCK, "t");
    payment.authorize(Money.of("50.00", USD), "auth-ref", CLOCK, "t");

    assertThatThrownBy(() -> payment.capture(Money.of("60.00", USD), "cap-ref", CLOCK, "t"))
        .isInstanceOf(CaptureExceedsAuthorizationException.class);
  }

  @Test
  void multipleCapturesAccumulateTowardAuthorizedAmount() {
    Payment payment = newPayment();
    payment.submit(CLOCK, "t");
    payment.authorize(Money.of("100.00", USD), "auth-ref", CLOCK, "t");

    payment.capture(Money.of("40.00", USD), "cap-1", CLOCK, "t");
    assertThat(payment.status()).isEqualTo(StandardPaymentState.PARTIALLY_CAPTURED);

    payment.capture(Money.of("60.00", USD), "cap-2", CLOCK, "t");
    assertThat(payment.status()).isEqualTo(StandardPaymentState.CAPTURED);
    assertThat(payment.totalCaptured()).isEqualTo(Money.of("100.00", USD));
    assertThat(payment.captures()).hasSize(2);
  }

  @Test
  void capturingBeforeAuthorizingIsRejected() {
    // No authorization yet means an authorized amount of zero, so this is reported as a capture
    // exceeding authorization rather than a raw lifecycle-transition failure — more informative.
    Payment payment = newPayment();
    payment.submit(CLOCK, "t");

    assertThatThrownBy(() -> payment.capture(Money.of("10.00", USD), "ref", CLOCK, "t"))
        .isInstanceOf(CaptureExceedsAuthorizationException.class);
  }

  @Test
  void settlingBeforeAuthorizationOrCaptureIsRejected() {
    Payment payment = newPayment();
    payment.submit(CLOCK, "t");

    assertThatThrownBy(() -> payment.settle(CLOCK, "t"))
        .isInstanceOf(IllegalPaymentStateTransitionException.class);
  }

  @Test
  void failedAttemptDoesNotForceLifecycleTransition() {
    Payment payment = newPayment();
    payment.submit(CLOCK, "t");

    var attempt =
        payment.recordAttempt(
            Money.of("100.00", USD),
            io.genfin.payment.attempt.AttemptResult.FAILED,
            FailureReason.of(FailureCategory.CARD_DECLINED, "declined"),
            null,
            CLOCK,
            "t");

    assertThat(attempt.succeeded()).isFalse();
    assertThat(payment.status()).isEqualTo(StandardPaymentState.PENDING);
    assertThat(payment.attempts()).hasSize(1);
  }

  @Test
  void explicitFailFiresFailedEvent() {
    Payment payment = newPayment();
    payment.submit(CLOCK, "t");
    payment.pullEvents();

    payment.fail(FailureReason.of(FailureCategory.PROVIDER_ERROR, "gateway down"), CLOCK, "t");

    assertThat(payment.status()).isEqualTo(StandardPaymentState.FAILED);
    assertThat(payment.pullEvents()).anyMatch(PaymentFailed.class::isInstance);
  }

  @Test
  void cancelFiresCancelledEvent() {
    Payment payment = newPayment();
    payment.pullEvents();

    payment.cancel("customer request", CLOCK, "t");

    assertThat(payment.status()).isEqualTo(StandardPaymentState.CANCELLED);
    List<DomainEvent> events = payment.pullEvents();
    assertThat(events).filteredOn(PaymentCancelled.class::isInstance).hasSize(1);
  }

  @Test
  void fullRefundAfterSettlementFiresRefundedEvent() {
    Payment payment = newPayment();
    payment.submit(CLOCK, "t");
    payment.authorize(Money.of("100.00", USD), "auth-ref", CLOCK, "t");
    payment.capture(Money.of("100.00", USD), "cap-ref", CLOCK, "t");
    payment.settle(CLOCK, "t");
    payment.pullEvents();

    payment.refund(Money.of("100.00", USD), CLOCK, "t");

    assertThat(payment.status()).isEqualTo(StandardPaymentState.REFUNDED);
    assertThat(payment.amountRefunded()).isEqualTo(Money.of("100.00", USD));
    assertThat(payment.pullEvents()).anyMatch(PaymentRefunded.class::isInstance);
  }

  @Test
  void partialRefundTransitionsToPartiallyRefunded() {
    Payment payment = newPayment();
    payment.submit(CLOCK, "t");
    payment.authorize(Money.of("100.00", USD), "auth-ref", CLOCK, "t");
    payment.capture(Money.of("100.00", USD), "cap-ref", CLOCK, "t");
    payment.settle(CLOCK, "t");

    payment.refund(Money.of("40.00", USD), CLOCK, "t");

    assertThat(payment.status()).isEqualTo(StandardPaymentState.PARTIALLY_REFUNDED);
  }

  @Test
  void versionIncrementsOnEveryMutation() {
    Payment payment = newPayment();
    int initial = payment.version();

    payment.updateMetadata(io.genfin.payment.metadata.PaymentMetadata.empty(), CLOCK, "t");

    assertThat(payment.version()).isEqualTo(initial + 1);
  }

  @Test
  void equalityIsByIdentity() {
    Payment a = newPayment();
    Payment b = newPayment();

    assertThat(a).isNotEqualTo(b);
    assertThat(a).isEqualTo(a);
  }
}
