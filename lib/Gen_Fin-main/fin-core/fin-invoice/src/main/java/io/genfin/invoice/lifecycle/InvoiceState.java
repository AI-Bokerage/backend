package io.genfin.invoice.lifecycle;

/**
 * A lifecycle state. Applications may implement this for custom states beyond {@link
 * StandardInvoiceState}.
 */
public interface InvoiceState {

  String code();
}
