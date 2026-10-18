package io.genfin.api.exception;

public class SecurityViolationException extends GenFinException {

  public SecurityViolationException(String message) {
    super(CoreErrorCode.SECURITY_VIOLATION, message);
  }
}
