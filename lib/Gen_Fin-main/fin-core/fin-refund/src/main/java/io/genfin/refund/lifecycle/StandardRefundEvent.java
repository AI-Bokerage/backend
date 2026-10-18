package io.genfin.refund.lifecycle;

public enum StandardRefundEvent implements RefundEvent {
  SUBMIT_FOR_APPROVAL,
  APPROVE,
  REJECT,
  PROCESS,
  COMPLETE,
  PARTIALLY_COMPLETE,
  FAIL,
  CANCEL,
  REVERSE,
  DISPUTE;

  @Override
  public String code() {
    return name();
  }
}
