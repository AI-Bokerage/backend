package io.genfin.invoice.internal.lifecycle;

import static io.genfin.invoice.lifecycle.StandardInvoiceEvent.CANCEL;
import static io.genfin.invoice.lifecycle.StandardInvoiceEvent.ISSUE;
import static io.genfin.invoice.lifecycle.StandardInvoiceEvent.MARK_OVERDUE;
import static io.genfin.invoice.lifecycle.StandardInvoiceEvent.RECORD_FULL_PAYMENT;
import static io.genfin.invoice.lifecycle.StandardInvoiceEvent.RECORD_PARTIAL_PAYMENT;
import static io.genfin.invoice.lifecycle.StandardInvoiceEvent.REFUND;
import static io.genfin.invoice.lifecycle.StandardInvoiceEvent.SEND;
import static io.genfin.invoice.lifecycle.StandardInvoiceEvent.VIEW;
import static io.genfin.invoice.lifecycle.StandardInvoiceEvent.VOID;
import static io.genfin.invoice.lifecycle.StandardInvoiceState.CANCELLED;
import static io.genfin.invoice.lifecycle.StandardInvoiceState.DRAFT;
import static io.genfin.invoice.lifecycle.StandardInvoiceState.ISSUED;
import static io.genfin.invoice.lifecycle.StandardInvoiceState.OVERDUE;
import static io.genfin.invoice.lifecycle.StandardInvoiceState.PAID;
import static io.genfin.invoice.lifecycle.StandardInvoiceState.PARTIALLY_PAID;
import static io.genfin.invoice.lifecycle.StandardInvoiceState.REFUNDED;
import static io.genfin.invoice.lifecycle.StandardInvoiceState.SENT;
import static io.genfin.invoice.lifecycle.StandardInvoiceState.VIEWED;
import static io.genfin.invoice.lifecycle.StandardInvoiceState.VOIDED;

import io.genfin.api.statemachine.StateMachine;
import io.genfin.api.statemachine.StateMachines;
import io.genfin.api.statemachine.Transition;
import io.genfin.invoice.lifecycle.InvoiceEvent;
import io.genfin.invoice.lifecycle.InvoiceState;
import io.genfin.invoice.port.lifecycle.LifecycleProvider;
import java.util.List;

/**
 * The standard invoice lifecycle: Draft → Issued → Sent → Viewed, with payment/cancel/void/refund
 * side paths.
 */
public final class DefaultLifecycleProvider implements LifecycleProvider {

  @Override
  public StateMachine<InvoiceState, InvoiceEvent> create(InvoiceState initialState) {
    return StateMachines.of(initialState, transitions());
  }

  private static List<Transition<InvoiceState, InvoiceEvent>> transitions() {
    return List.of(
        new Transition<>(DRAFT, ISSUE, ISSUED),
        new Transition<>(DRAFT, CANCEL, CANCELLED),
        new Transition<>(ISSUED, SEND, SENT),
        new Transition<>(ISSUED, CANCEL, CANCELLED),
        new Transition<>(ISSUED, VOID, VOIDED),
        new Transition<>(ISSUED, MARK_OVERDUE, OVERDUE),
        new Transition<>(ISSUED, RECORD_PARTIAL_PAYMENT, PARTIALLY_PAID),
        new Transition<>(ISSUED, RECORD_FULL_PAYMENT, PAID),
        new Transition<>(SENT, VIEW, VIEWED),
        new Transition<>(SENT, VOID, VOIDED),
        new Transition<>(SENT, MARK_OVERDUE, OVERDUE),
        new Transition<>(SENT, RECORD_PARTIAL_PAYMENT, PARTIALLY_PAID),
        new Transition<>(SENT, RECORD_FULL_PAYMENT, PAID),
        new Transition<>(VIEWED, VOID, VOIDED),
        new Transition<>(VIEWED, MARK_OVERDUE, OVERDUE),
        new Transition<>(VIEWED, RECORD_PARTIAL_PAYMENT, PARTIALLY_PAID),
        new Transition<>(VIEWED, RECORD_FULL_PAYMENT, PAID),
        new Transition<>(PARTIALLY_PAID, RECORD_PARTIAL_PAYMENT, PARTIALLY_PAID),
        new Transition<>(PARTIALLY_PAID, RECORD_FULL_PAYMENT, PAID),
        new Transition<>(PARTIALLY_PAID, VOID, VOIDED),
        new Transition<>(OVERDUE, RECORD_PARTIAL_PAYMENT, PARTIALLY_PAID),
        new Transition<>(OVERDUE, RECORD_FULL_PAYMENT, PAID),
        new Transition<>(OVERDUE, VOID, VOIDED),
        new Transition<>(PAID, REFUND, REFUNDED));
  }
}
