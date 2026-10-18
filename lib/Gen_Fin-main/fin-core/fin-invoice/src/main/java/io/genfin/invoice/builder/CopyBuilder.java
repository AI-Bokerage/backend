package io.genfin.invoice.builder;

import io.genfin.api.port.time.ClockProvider;
import io.genfin.invoice.invoice.Invoice;
import io.genfin.invoice.invoice.InvoiceBuilder;

/**
 * Starts a fresh {@link InvoiceBuilder} pre-populated from an existing invoice's header only (no
 * lines/history).
 */
public final class CopyBuilder {

  private CopyBuilder() {}

  public static InvoiceBuilder from(Invoice source) {
    return InvoiceBuilder.newInvoice()
        .type(source.type())
        .currency(source.currency())
        .dueDate(source.dates().dueDate());
  }

  public static InvoiceBuilder from(Invoice source, ClockProvider clockProvider, String actor) {
    return from(source).clockProvider(clockProvider).actor(actor);
  }
}
