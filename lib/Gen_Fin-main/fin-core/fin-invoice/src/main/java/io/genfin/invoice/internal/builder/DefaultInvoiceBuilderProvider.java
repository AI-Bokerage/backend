package io.genfin.invoice.internal.builder;

import io.genfin.invoice.invoice.InvoiceBuilder;
import io.genfin.invoice.port.builder.InvoiceBuilderProvider;

public final class DefaultInvoiceBuilderProvider implements InvoiceBuilderProvider {

  @Override
  public InvoiceBuilder newBuilder() {
    return InvoiceBuilder.newInvoice();
  }
}
