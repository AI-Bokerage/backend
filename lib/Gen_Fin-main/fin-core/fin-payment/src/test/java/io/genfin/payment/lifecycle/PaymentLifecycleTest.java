package io.genfin.payment.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.api.statemachine.StateMachine;
import org.junit.jupiter.api.Test;

class PaymentLifecycleTest {

  @Test
  void standardLifecycleFollowsSubmitAuthorizeCaptureSettlePath() {
    StateMachine<PaymentState, PaymentEvent> lifecycle = PaymentLifecycles.created();

    assertThat(lifecycle.fire(StandardPaymentEvent.SUBMIT).isAllowed()).isTrue();
    assertThat(lifecycle.fire(StandardPaymentEvent.AUTHORIZE).isAllowed()).isTrue();
    assertThat(lifecycle.fire(StandardPaymentEvent.CAPTURE).isAllowed()).isTrue();
    assertThat(lifecycle.fire(StandardPaymentEvent.SETTLE).isAllowed()).isTrue();
    assertThat(lifecycle.currentState()).isEqualTo(StandardPaymentState.SETTLED);
  }

  @Test
  void settledPaymentCanBeDisputedThenChargedBack() {
    StateMachine<PaymentState, PaymentEvent> lifecycle = PaymentLifecycles.created();
    lifecycle.fire(StandardPaymentEvent.SUBMIT);
    lifecycle.fire(StandardPaymentEvent.AUTHORIZE);
    lifecycle.fire(StandardPaymentEvent.CAPTURE);
    lifecycle.fire(StandardPaymentEvent.SETTLE);

    lifecycle.fire(StandardPaymentEvent.DISPUTE);
    assertThat(lifecycle.fire(StandardPaymentEvent.MARK_CHARGEBACK).isAllowed()).isTrue();
    assertThat(lifecycle.currentState()).isEqualTo(StandardPaymentState.CHARGEBACK);
  }

  @Test
  void createdCannotJumpDirectlyToCaptured() {
    StateMachine<PaymentState, PaymentEvent> lifecycle = PaymentLifecycles.created();

    assertThat(lifecycle.fire(StandardPaymentEvent.CAPTURE).isAllowed()).isFalse();
  }

  @Test
  void partialAuthorizationCanBePartiallyCapturedThenCompleted() {
    StateMachine<PaymentState, PaymentEvent> lifecycle = PaymentLifecycles.created();
    lifecycle.fire(StandardPaymentEvent.SUBMIT);
    lifecycle.fire(StandardPaymentEvent.PARTIALLY_AUTHORIZE);

    assertThat(lifecycle.fire(StandardPaymentEvent.CAPTURE).isAllowed()).isTrue();
    assertThat(lifecycle.currentState()).isEqualTo(StandardPaymentState.PARTIALLY_CAPTURED);
  }
}
