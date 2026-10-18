package io.genfin.invoice.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.api.statemachine.StateMachine;
import io.genfin.api.statemachine.TransitionResult;
import org.junit.jupiter.api.Test;

class InvoiceLifecycleTest {

  @Test
  void standardLifecycleFollowsIssueSendViewPath() {
    StateMachine<InvoiceState, InvoiceEvent> lifecycle = InvoiceLifecycles.draft();

    assertThat(lifecycle.currentState()).isEqualTo(StandardInvoiceState.DRAFT);
    assertThat(lifecycle.fire(StandardInvoiceEvent.ISSUE).isAllowed()).isTrue();
    assertThat(lifecycle.fire(StandardInvoiceEvent.SEND).isAllowed()).isTrue();
    assertThat(lifecycle.fire(StandardInvoiceEvent.VIEW).isAllowed()).isTrue();
    assertThat(lifecycle.currentState()).isEqualTo(StandardInvoiceState.VIEWED);
  }

  @Test
  void paidInvoiceCanBeRefunded() {
    StateMachine<InvoiceState, InvoiceEvent> lifecycle = InvoiceLifecycles.draft();
    lifecycle.fire(StandardInvoiceEvent.ISSUE);
    lifecycle.fire(StandardInvoiceEvent.RECORD_FULL_PAYMENT);

    TransitionResult<InvoiceState> refunded = lifecycle.fire(StandardInvoiceEvent.REFUND);

    assertThat(refunded.isAllowed()).isTrue();
    assertThat(lifecycle.currentState()).isEqualTo(StandardInvoiceState.REFUNDED);
  }

  @Test
  void draftCannotJumpDirectlyToPaid() {
    StateMachine<InvoiceState, InvoiceEvent> lifecycle = InvoiceLifecycles.draft();

    assertThat(lifecycle.fire(StandardInvoiceEvent.RECORD_FULL_PAYMENT).isAllowed()).isFalse();
    assertThat(lifecycle.currentState()).isEqualTo(StandardInvoiceState.DRAFT);
  }

  @Test
  void voidedInvoiceIsTerminalForFurtherPaymentEvents() {
    StateMachine<InvoiceState, InvoiceEvent> lifecycle = InvoiceLifecycles.draft();
    lifecycle.fire(StandardInvoiceEvent.ISSUE);
    lifecycle.fire(StandardInvoiceEvent.VOID);

    assertThat(lifecycle.fire(StandardInvoiceEvent.RECORD_FULL_PAYMENT).isAllowed()).isFalse();
  }
}
