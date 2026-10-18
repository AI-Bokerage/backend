package io.genfin.invoice.factory;

import io.genfin.api.port.time.ClockProvider;
import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.api.time.ClockProviders;
import io.genfin.invoice.invoice.InvoiceBuilder;
import io.genfin.invoice.lifecycle.StandardInvoiceState;
import io.genfin.invoice.numbering.InvoiceNumber;
import io.genfin.invoice.numbering.NumberGenerationContext;
import io.genfin.invoice.port.builder.InvoiceBuilderProvider;
import io.genfin.invoice.port.lifecycle.LifecycleProvider;
import io.genfin.invoice.port.numbering.InvoiceNumberGenerator;
import io.genfin.money.currency.Currency;
import java.time.Instant;

/** Config/registry-bound entry point for creating invoices and invoice numbers. */
public final class InvoiceFactory {

  private final ExtensionRegistry registry;
  private final ClockProvider clockProvider;

  private InvoiceFactory(ExtensionRegistry registry, ClockProvider clockProvider) {
    this.registry = registry;
    this.clockProvider = clockProvider;
  }

  public static InvoiceFactory using(ExtensionRegistry registry) {
    return new InvoiceFactory(registry, ClockProviders.system());
  }

  public static InvoiceFactory using(ExtensionRegistry registry, ClockProvider clockProvider) {
    return new InvoiceFactory(registry, clockProvider);
  }

  public InvoiceBuilder newDraft(Currency currency, Instant dueDate, String actor) {
    InvoiceBuilder builder =
        registry
            .find(InvoiceBuilderProvider.class)
            .map(InvoiceBuilderProvider::newBuilder)
            .orElseGet(InvoiceBuilder::newInvoice);
    builder.currency(currency).dueDate(dueDate).clockProvider(clockProvider).actor(actor);
    registry
        .find(LifecycleProvider.class)
        .ifPresent(provider -> builder.lifecycle(provider.create(StandardInvoiceState.DRAFT)));
    return builder;
  }

  public InvoiceNumber nextNumber(NumberGenerationContext context) {
    InvoiceNumberGenerator generator =
        registry
            .find(InvoiceNumberGenerator.class)
            .orElseThrow(() -> new IllegalStateException("No InvoiceNumberGenerator registered."));
    return generator.generate(context);
  }
}
