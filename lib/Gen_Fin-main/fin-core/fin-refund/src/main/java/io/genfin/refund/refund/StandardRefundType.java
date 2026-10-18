package io.genfin.refund.refund;

public enum StandardRefundType implements RefundType {
  FULL,
  PARTIAL,
  GOODWILL,
  CHARGEBACK_REVERSAL;

  @Override
  public String code() {
    return name();
  }
}
