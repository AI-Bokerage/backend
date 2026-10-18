package io.genfin.api.exception;

/**
 * Generic, infrastructure-level error codes. Finance-specific codes belong to their own modules.
 */
public enum CoreErrorCode implements ErrorCode {
  VALIDATION_FAILED(Severity.ERROR, ErrorCategory.VALIDATION, false, "Validation failed."),
  CONFIGURATION_INVALID(
      Severity.CRITICAL, ErrorCategory.CONFIGURATION, false, "Invalid configuration."),
  SERIALIZATION_FAILED(Severity.ERROR, ErrorCategory.SERIALIZATION, false, "Serialization failed."),
  ILLEGAL_STATE_TRANSITION(Severity.ERROR, ErrorCategory.STATE, false, "Illegal state transition."),
  INTEGRATION_FAILURE(
      Severity.ERROR, ErrorCategory.INTEGRATION, true, "Downstream integration failed."),
  CONCURRENT_MODIFICATION(
      Severity.ERROR, ErrorCategory.CONCURRENCY, true, "Concurrent modification detected."),
  SECURITY_VIOLATION(Severity.CRITICAL, ErrorCategory.SECURITY, false, "Security violation."),
  OPERATION_TIMED_OUT(Severity.ERROR, ErrorCategory.TIMEOUT, true, "Operation timed out."),
  UNKNOWN(Severity.ERROR, ErrorCategory.UNKNOWN, false, "Unknown error.");

  private final Severity severity;
  private final ErrorCategory category;
  private final boolean retryable;
  private final String defaultMessage;

  CoreErrorCode(
      Severity severity, ErrorCategory category, boolean retryable, String defaultMessage) {
    this.severity = severity;
    this.category = category;
    this.retryable = retryable;
    this.defaultMessage = defaultMessage;
  }

  @Override
  public String code() {
    return "CORE-" + name();
  }

  @Override
  public Severity severity() {
    return severity;
  }

  @Override
  public ErrorCategory category() {
    return category;
  }

  @Override
  public boolean retryable() {
    return retryable;
  }

  @Override
  public String defaultMessage() {
    return defaultMessage;
  }
}
