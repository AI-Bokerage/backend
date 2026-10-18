package io.genfin.payment.payment;

public enum StandardPaymentType implements PaymentType {
  ONE_TIME,
  RECURRING,
  INSTALLMENT,
  PARTIAL;

  @Override
  public String code() {
    return name();
  }
}
