package io.genfin.api.exception;

/**
 * Machine-readable error identity. Implementations are typically enums so every error condition in
 * the framework has a stable code instead of a hardcoded string.
 */
public interface ErrorCode {

  String code();

  Severity severity();

  ErrorCategory category();

  boolean retryable();

  String defaultMessage();
}
