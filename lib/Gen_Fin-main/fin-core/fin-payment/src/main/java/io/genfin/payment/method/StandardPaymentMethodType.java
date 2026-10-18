package io.genfin.payment.method;

public enum StandardPaymentMethodType implements PaymentMethodType {
  CARD,
  BANK_TRANSFER,
  UPI,
  WALLET,
  ACH,
  SEPA,
  CASH,
  CHEQUE,
  CRYPTO,
  INTERNAL_CREDIT,
  GIFT_CARD;

  @Override
  public String code() {
    return name();
  }
}
