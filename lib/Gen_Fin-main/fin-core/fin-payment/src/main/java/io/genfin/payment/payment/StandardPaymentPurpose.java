package io.genfin.payment.payment;

public enum StandardPaymentPurpose implements PaymentPurpose {
  INVOICE_PAYMENT,
  SUBSCRIPTION_CHARGE,
  DEPOSIT,
  TOP_UP,
  REFUND,
  PAYOUT,
  ADJUSTMENT,
  MANUAL;

  @Override
  public String code() {
    return name();
  }
}
