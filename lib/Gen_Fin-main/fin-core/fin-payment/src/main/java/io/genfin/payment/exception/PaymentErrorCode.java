package io.genfin.payment.exception;

import io.genfin.api.exception.ErrorCategory;
import io.genfin.api.exception.ErrorCode;
import io.genfin.api.exception.Severity;

public enum PaymentErrorCode implements ErrorCode {
  VALIDATION_FAILED(Severity.ERROR, ErrorCategory.VALIDATION, false, "Payment validation failed."),
  ILLEGAL_STATE_TRANSITION(
      Severity.ERROR, ErrorCategory.STATE, false, "Illegal payment lifecycle transition."),
  CURRENCY_MISMATCH(
      Severity.ERROR, ErrorCategory.VALIDATION, false, "Payment amount currency does not match."),
  CAPTURE_EXCEEDS_AUTHORIZATION(
      Severity.ERROR, ErrorCategory.VALIDATION, false, "Capture amount exceeds authorized amount."),
  DUPLICATE_IDEMPOTENCY_KEY(
      Severity.ERROR,
      ErrorCategory.VALIDATION,
      false,
      "Idempotency key was already used with a different request."),
  UNSUPPORTED_PAYMENT_METHOD(
      Severity.ERROR, ErrorCategory.VALIDATION, false, "Payment method is not supported/enabled."),
  SESSION_EXPIRED(Severity.ERROR, ErrorCategory.STATE, false, "Payment session has expired."),
  GATEWAY_INTEGRATION_FAILED(
      Severity.ERROR, ErrorCategory.INTEGRATION, true, "Payment gateway integration failed."),
  INVALID_PAYMENT_FORMAT(
      Severity.ERROR,
      ErrorCategory.SERIALIZATION,
      false,
      "Could not parse serialized payment payload.");

  private final Severity severity;
  private final ErrorCategory category;
  private final boolean retryable;
  private final String defaultMessage;

  PaymentErrorCode(
      Severity severity, ErrorCategory category, boolean retryable, String defaultMessage) {
    this.severity = severity;
    this.category = category;
    this.retryable = retryable;
    this.defaultMessage = defaultMessage;
  }

  @Override
  public String code() {
    return "PAYMENT-" + name();
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
