package io.genfin.refund.reason;

public enum StandardRefundReason implements RefundReason {
  CUSTOMER_REQUEST,
  DUPLICATE_CHARGE,
  FRAUD,
  PRODUCT_RETURN,
  PRICING_ADJUSTMENT,
  SERVICE_FAILURE,
  MANUAL_CORRECTION,
  CHARGEBACK_RESOLUTION;

  @Override
  public String code() {
    return name();
  }
}
