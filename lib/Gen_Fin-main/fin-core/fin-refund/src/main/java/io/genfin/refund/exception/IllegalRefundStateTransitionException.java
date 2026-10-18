package io.genfin.refund.exception;

import io.genfin.api.exception.GenFinException;

public class IllegalRefundStateTransitionException extends GenFinException {

  public IllegalRefundStateTransitionException(String message) {
    super(RefundErrorCode.ILLEGAL_STATE_TRANSITION, message);
  }
}
