package io.genfin.refund.exception;

import io.genfin.api.exception.ErrorCategory;
import io.genfin.api.exception.ErrorCode;
import io.genfin.api.exception.Severity;

public enum RefundErrorCode implements ErrorCode {
  VALIDATION_FAILED(Severity.ERROR, ErrorCategory.VALIDATION, false, "Refund validation failed."),
  ILLEGAL_STATE_TRANSITION(
      Severity.ERROR, ErrorCategory.STATE, false, "Illegal refund lifecycle transition."),
  CURRENCY_MISMATCH(
      Severity.ERROR, ErrorCategory.VALIDATION, false, "Refund amount currency does not match."),
  REFUND_EXCEEDS_REFUNDABLE_BALANCE(
      Severity.ERROR,
      ErrorCategory.VALIDATION,
      false,
      "Refund amount exceeds the remaining refundable balance."),
  DUPLICATE_REFUND_REFERENCE(
      Severity.ERROR,
      ErrorCategory.VALIDATION,
      false,
      "Refund reference was already used by another refund."),
  INVALID_REFUND_REASON(
      Severity.ERROR, ErrorCategory.VALIDATION, false, "Refund reason is not permitted."),
  REFUND_WINDOW_EXPIRED(
      Severity.ERROR, ErrorCategory.STATE, false, "Refund window for this payment has expired."),
  APPROVAL_REQUIRED(
      Severity.ERROR, ErrorCategory.STATE, false, "Refund requires approval before processing."),
  GATEWAY_INTEGRATION_FAILED(
      Severity.ERROR, ErrorCategory.INTEGRATION, true, "Refund gateway integration failed."),
  INVALID_REFUND_FORMAT(
      Severity.ERROR,
      ErrorCategory.SERIALIZATION,
      false,
      "Could not parse serialized refund payload.");

  private final Severity severity;
  private final ErrorCategory category;
  private final boolean retryable;
  private final String defaultMessage;

  RefundErrorCode(
      Severity severity, ErrorCategory category, boolean retryable, String defaultMessage) {
    this.severity = severity;
    this.category = category;
    this.retryable = retryable;
    this.defaultMessage = defaultMessage;
  }

  @Override
  public String code() {
    return "REFUND-" + name();
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
