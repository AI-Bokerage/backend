package io.genfin.invoice.invoice;

public enum StandardInvoiceType implements InvoiceType {
  STANDARD,
  CREDIT_NOTE,
  DEBIT_NOTE,
  PROFORMA,
  RECURRING;

  @Override
  public String code() {
    return name();
  }
}
