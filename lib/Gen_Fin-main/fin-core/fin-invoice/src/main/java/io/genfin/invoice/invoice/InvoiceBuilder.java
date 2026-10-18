package io.genfin.invoice.invoice;

import io.genfin.api.port.time.ClockProvider;
import io.genfin.api.statemachine.StateMachine;
import io.genfin.api.time.ClockProviders;
import io.genfin.invoice.id.InvoiceId;
import io.genfin.invoice.lifecycle.InvoiceEvent;
import io.genfin.invoice.lifecycle.InvoiceLifecycles;
import io.genfin.invoice.lifecycle.InvoiceState;
import io.genfin.invoice.lifecycle.StandardInvoiceState;
import io.genfin.money.currency.Currency;
import java.time.Instant;

/**
 * Builds {@link Invoice} aggregates. Consumers must not call the {@code Invoice} constructor
 * directly.
 */
public final class InvoiceBuilder {

  private InvoiceId id;
  private InvoiceType type = StandardInvoiceType.STANDARD;
  private Currency currency;
  private Instant dueDate;
  private StateMachine<InvoiceState, InvoiceEvent> lifecycle;
  private ClockProvider clockProvider = ClockProviders.system();
  private String actor;

  private InvoiceBuilder() {}

  public static InvoiceBuilder newInvoice() {
    return new InvoiceBuilder();
  }

  public InvoiceBuilder id(InvoiceId id) {
    this.id = id;
    return this;
  }

  public InvoiceBuilder type(InvoiceType type) {
    this.type = type;
    return this;
  }

  public InvoiceBuilder currency(Currency currency) {
    this.currency = currency;
    return this;
  }

  public InvoiceBuilder dueDate(Instant dueDate) {
    this.dueDate = dueDate;
    return this;
  }

  public InvoiceBuilder lifecycle(StateMachine<InvoiceState, InvoiceEvent> lifecycle) {
    this.lifecycle = lifecycle;
    return this;
  }

  public InvoiceBuilder clockProvider(ClockProvider clockProvider) {
    this.clockProvider = clockProvider;
    return this;
  }

  public InvoiceBuilder actor(String actor) {
    this.actor = actor;
    return this;
  }

  public Invoice build() {
    StateMachine<InvoiceState, InvoiceEvent> resolvedLifecycle =
        lifecycle != null
            ? lifecycle
            : InvoiceLifecycles.standard().create(StandardInvoiceState.DRAFT);
    return new Invoice(
        id == null ? InvoiceId.generate() : id,
        type,
        currency,
        InvoiceDates.unissued(dueDate),
        resolvedLifecycle,
        clockProvider,
        actor);
  }
}
