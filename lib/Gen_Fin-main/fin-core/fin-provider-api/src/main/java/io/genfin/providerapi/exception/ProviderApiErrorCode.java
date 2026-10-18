package io.genfin.providerapi.exception;

import io.genfin.api.exception.ErrorCategory;
import io.genfin.api.exception.ErrorCode;
import io.genfin.api.exception.Severity;

public enum ProviderApiErrorCode implements ErrorCode {
  INVALID_WEBHOOK_SIGNATURE(
      Severity.CRITICAL, ErrorCategory.SECURITY, false, "Webhook signature verification failed."),
  PROVIDER_UNAVAILABLE(
      Severity.ERROR, ErrorCategory.INTEGRATION, true, "No usable provider could be resolved."),
  CIRCUIT_OPEN(
      Severity.ERROR, ErrorCategory.STATE, true, "Circuit breaker is open for this provider."),
  RATE_LIMITED(Severity.WARNING, ErrorCategory.INTEGRATION, true, "Provider rate limit exceeded.");

  private final Severity severity;
  private final ErrorCategory category;
  private final boolean retryable;
  private final String defaultMessage;

  ProviderApiErrorCode(
      Severity severity, ErrorCategory category, boolean retryable, String defaultMessage) {
    this.severity = severity;
    this.category = category;
    this.retryable = retryable;
    this.defaultMessage = defaultMessage;
  }

  @Override
  public String code() {
    return "PROVIDER-" + name();
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
