package io.genfin.money.exception;

import io.genfin.api.exception.ErrorCategory;
import io.genfin.api.exception.ErrorCode;
import io.genfin.api.exception.Severity;

/** Money-engine-specific error codes. No jurisdiction-specific (tax) codes live here. */
public enum MoneyErrorCode implements ErrorCode {
  CURRENCY_MISMATCH(
      Severity.ERROR,
      ErrorCategory.VALIDATION,
      false,
      "Money operation across mismatched currencies without a conversion strategy."),
  UNKNOWN_CURRENCY(
      Severity.ERROR, ErrorCategory.VALIDATION, false, "Currency not found in registry."),
  DUPLICATE_CURRENCY(
      Severity.ERROR, ErrorCategory.VALIDATION, false, "Currency already registered."),
  ARITHMETIC_OVERFLOW(
      Severity.CRITICAL, ErrorCategory.STATE, false, "Monetary arithmetic overflow."),
  UNSUPPORTED_CONVERSION(
      Severity.ERROR,
      ErrorCategory.INTEGRATION,
      true,
      "No exchange rate available for requested conversion."),
  ALLOCATION_ERROR(
      Severity.ERROR,
      ErrorCategory.VALIDATION,
      false,
      "Allocation could not be computed without losing value."),
  INVALID_MONEY_FORMAT(
      Severity.ERROR, ErrorCategory.SERIALIZATION, false, "Could not parse text as Money.");

  private final Severity severity;
  private final ErrorCategory category;
  private final boolean retryable;
  private final String defaultMessage;

  MoneyErrorCode(
      Severity severity, ErrorCategory category, boolean retryable, String defaultMessage) {
    this.severity = severity;
    this.category = category;
    this.retryable = retryable;
    this.defaultMessage = defaultMessage;
  }

  @Override
  public String code() {
    return "MONEY-" + name();
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
