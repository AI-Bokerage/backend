package io.genfin.invoice.lifecycle;

public enum StandardInvoiceState implements InvoiceState {
  DRAFT,
  ISSUED,
  SENT,
  VIEWED,
  PARTIALLY_PAID,
  PAID,
  OVERDUE,
  CANCELLED,
  VOIDED,
  REFUNDED;

  @Override
  public String code() {
    return name();
  }
}
