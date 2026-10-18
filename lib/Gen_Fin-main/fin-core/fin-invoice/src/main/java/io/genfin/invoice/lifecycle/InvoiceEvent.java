package io.genfin.invoice.lifecycle;

/**
 * A lifecycle trigger. Applications may implement this for custom events beyond {@link
 * StandardInvoiceEvent}.
 */
public interface InvoiceEvent {

  String code();
}
