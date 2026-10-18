package io.genfin.api.exception;

public class TimeoutException extends GenFinException {

  public TimeoutException(String message) {
    super(CoreErrorCode.OPERATION_TIMED_OUT, message);
  }
}
