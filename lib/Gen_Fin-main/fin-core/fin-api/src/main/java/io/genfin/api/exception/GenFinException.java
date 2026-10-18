package io.genfin.api.exception;

import java.io.Serial;
import java.util.Map;

/**
 * Root of the Gen-Fin exception hierarchy. Immutable, carries a machine-readable {@link ErrorCode},
 * structured metadata, and supports nested causes.
 */
public class GenFinException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final ErrorCode errorCode;
  private final Map<String, Object> metadata;

  public GenFinException(ErrorCode errorCode, String message) {
    this(errorCode, message, null, Map.of());
  }

  public GenFinException(ErrorCode errorCode, String message, Throwable cause) {
    this(errorCode, message, cause, Map.of());
  }

  public GenFinException(
      ErrorCode errorCode, String message, Throwable cause, Map<String, Object> metadata) {
    super(message, cause);
    this.errorCode = errorCode;
    this.metadata = Map.copyOf(metadata);
  }

  public final ErrorCode errorCode() {
    return errorCode;
  }

  public final Map<String, Object> metadata() {
    return metadata;
  }
}
