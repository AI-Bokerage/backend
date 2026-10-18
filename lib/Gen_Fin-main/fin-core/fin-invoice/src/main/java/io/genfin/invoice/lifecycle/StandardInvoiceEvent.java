package io.genfin.invoice.lifecycle;

public enum StandardInvoiceEvent implements InvoiceEvent {
  ISSUE,
  SEND,
  VIEW,
  RECORD_PARTIAL_PAYMENT,
  RECORD_FULL_PAYMENT,
  MARK_OVERDUE,
  CANCEL,
  VOID,
  REFUND;

  @Override
  public String code() {
    return name();
  }
}
