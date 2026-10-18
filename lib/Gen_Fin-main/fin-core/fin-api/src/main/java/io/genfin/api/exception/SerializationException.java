package io.genfin.api.exception;

public class SerializationException extends GenFinException {

  public SerializationException(String message, Throwable cause) {
    super(CoreErrorCode.SERIALIZATION_FAILED, message, cause);
  }
}
