package io.genfin.money.exception;

public class AllocationException extends io.genfin.api.exception.GenFinException {

  public AllocationException(String message) {
    super(MoneyErrorCode.ALLOCATION_ERROR, message);
  }
}
