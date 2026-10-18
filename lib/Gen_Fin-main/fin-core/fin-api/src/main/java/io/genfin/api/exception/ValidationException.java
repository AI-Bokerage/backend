package io.genfin.api.exception;

import java.util.Map;

public class ValidationException extends GenFinException {

  public ValidationException(String message) {
    super(CoreErrorCode.VALIDATION_FAILED, message);
  }

  public ValidationException(String message, Map<String, Object> metadata) {
    super(CoreErrorCode.VALIDATION_FAILED, message, null, metadata);
  }
}
