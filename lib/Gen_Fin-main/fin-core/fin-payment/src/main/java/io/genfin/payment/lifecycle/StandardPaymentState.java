package io.genfin.payment.lifecycle;

public enum StandardPaymentState implements PaymentState {
  CREATED,
  PENDING,
  AUTHORIZED,
  PARTIALLY_AUTHORIZED,
  CAPTURED,
  PARTIALLY_CAPTURED,
  SETTLED,
  FAILED,
  CANCELLED,
  EXPIRED,
  REFUNDED,
  PARTIALLY_REFUNDED,
  CHARGEBACK,
  DISPUTED;

  @Override
  public String code() {
    return name();
  }
}
