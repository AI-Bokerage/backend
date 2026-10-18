package io.genfin.invoice.exception;

import io.genfin.api.exception.ErrorCategory;
import io.genfin.api.exception.ErrorCode;
import io.genfin.api.exception.Severity;

public enum InvoiceErrorCode implements ErrorCode {
  VALIDATION_FAILED(Severity.ERROR, ErrorCategory.VALIDATION, false, "Invoice validation failed."),
  ILLEGAL_STATE_TRANSITION(
      Severity.ERROR, ErrorCategory.STATE, false, "Illegal invoice lifecycle transition."),
  CURRENCY_MISMATCH(
      Severity.ERROR,
      ErrorCategory.VALIDATION,
      false,
      "Invoice line/adjustment currency does not match the invoice currency."),
  NUMBER_GENERATION_FAILED(
      Severity.ERROR, ErrorCategory.INTEGRATION, true, "Could not generate an invoice number."),
  UNKNOWN_REFERENCE_TYPE(
      Severity.ERROR, ErrorCategory.VALIDATION, false, "Unknown reference type."),
  INVALID_INVOICE_FORMAT(
      Severity.ERROR,
      ErrorCategory.SERIALIZATION,
      false,
      "Could not parse serialized invoice payload.");

  private final Severity severity;
  private final ErrorCategory category;
  private final boolean retryable;
  private final String defaultMessage;

  InvoiceErrorCode(
      Severity severity, ErrorCategory category, boolean retryable, String defaultMessage) {
    this.severity = severity;
    this.category = category;
    this.retryable = retryable;
    this.defaultMessage = defaultMessage;
  }

  @Override
  public String code() {
    return "INVOICE-" + name();
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
