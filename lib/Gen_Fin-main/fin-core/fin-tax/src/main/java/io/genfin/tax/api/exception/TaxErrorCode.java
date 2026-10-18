package io.genfin.tax.api.exception;

import io.genfin.api.exception.ErrorCategory;
import io.genfin.api.exception.ErrorCode;
import io.genfin.api.exception.Severity;

/** Tax-engine-specific error codes. Mirrors {@code io.genfin.money.exception.MoneyErrorCode}. */
public enum TaxErrorCode implements ErrorCode {
  UNSUPPORTED_JURISDICTION(
      Severity.INFO,
      ErrorCategory.VALIDATION,
      false,
      "No tax rule exists for this jurisdiction pairing; treated as NO_TAX."),
  INVALID_TAX_PROFILE(
      Severity.ERROR, ErrorCategory.VALIDATION, false, "A party's tax profile is inconsistent."),
  MISSING_TAX_PROFILE(
      Severity.ERROR,
      ErrorCategory.VALIDATION,
      false,
      "A required seller or buyer tax profile was not supplied."),
  UNSUPPORTED_TRANSACTION(
      Severity.ERROR,
      ErrorCategory.VALIDATION,
      false,
      "This transaction shape is not resolvable by Tax Engine V1.");

  private final Severity severity;
  private final ErrorCategory category;
  private final boolean retryable;
  private final String defaultMessage;

  TaxErrorCode(
      Severity severity, ErrorCategory category, boolean retryable, String defaultMessage) {
    this.severity = severity;
    this.category = category;
    this.retryable = retryable;
    this.defaultMessage = defaultMessage;
  }

  @Override
  public String code() {
    return "TAX-" + name();
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
