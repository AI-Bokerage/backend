package io.genfin.refund.lifecycle;

public enum StandardRefundStatus implements RefundStatus {
  REQUESTED,
  PENDING_APPROVAL,
  APPROVED,
  REJECTED,
  PROCESSING,
  PARTIALLY_PROCESSED,
  COMPLETED,
  FAILED,
  CANCELLED,
  REVERSED,
  DISPUTED;

  @Override
  public String code() {
    return name();
  }
}
