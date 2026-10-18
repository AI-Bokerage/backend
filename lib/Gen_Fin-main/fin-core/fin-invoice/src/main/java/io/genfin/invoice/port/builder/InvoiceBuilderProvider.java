package io.genfin.invoice.port.builder;

import io.genfin.api.port.spi.Extension;
import io.genfin.invoice.invoice.InvoiceBuilder;

/**
 * Supplies a pre-configured {@link InvoiceBuilder} (e.g. with an organization's default lifecycle
 * wired in).
 */
public interface InvoiceBuilderProvider extends Extension {

  InvoiceBuilder newBuilder();
}
