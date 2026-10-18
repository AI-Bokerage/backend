package io.genfin.api.exception;

public class ConcurrencyException extends GenFinException {

  public ConcurrencyException(String message) {
    super(CoreErrorCode.CONCURRENT_MODIFICATION, message);
  }
}
