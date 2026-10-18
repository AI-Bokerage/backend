package io.genfin.payment.lifecycle;

public enum StandardPaymentEvent implements PaymentEvent {
  SUBMIT,
  AUTHORIZE,
  PARTIALLY_AUTHORIZE,
  CAPTURE,
  PARTIALLY_CAPTURE,
  SETTLE,
  FAIL,
  CANCEL,
  EXPIRE,
  REFUND,
  PARTIALLY_REFUND,
  DISPUTE,
  MARK_CHARGEBACK;

  @Override
  public String code() {
    return name();
  }
}
