package io.genfin.api.exception;

public class IntegrationException extends GenFinException {

  public IntegrationException(String message, Throwable cause) {
    super(CoreErrorCode.INTEGRATION_FAILURE, message, cause);
  }
}
