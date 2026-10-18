package io.genfin.document.api.exception;

import io.genfin.api.exception.ErrorCategory;
import io.genfin.api.exception.ErrorCode;
import io.genfin.api.exception.Severity;

/**
 * Document-engine-specific error codes. Mirrors {@code io.genfin.money.exception.MoneyErrorCode}.
 */
public enum DocumentErrorCode implements ErrorCode {
  BRAND_NOT_FOUND(Severity.ERROR, ErrorCategory.VALIDATION, false, "Brand not found in registry."),
  LETTERHEAD_NOT_FOUND(
      Severity.ERROR, ErrorCategory.VALIDATION, false, "No letterhead provider supports the id."),
  QR_CODE_NOT_FOUND(
      Severity.ERROR, ErrorCategory.VALIDATION, false, "No QR code provider supports the key."),
  RENDERER_NOT_FOUND(
      Severity.ERROR, ErrorCategory.VALIDATION, false, "No renderer registered for the id."),
  TEMPLATE_NOT_FOUND(
      Severity.ERROR, ErrorCategory.VALIDATION, false, "Template not found in registry."),
  RENDER_FAILED(Severity.ERROR, ErrorCategory.STATE, false, "Document rendering failed.");

  private final Severity severity;
  private final ErrorCategory category;
  private final boolean retryable;
  private final String defaultMessage;

  DocumentErrorCode(
      Severity severity, ErrorCategory category, boolean retryable, String defaultMessage) {
    this.severity = severity;
    this.category = category;
    this.retryable = retryable;
    this.defaultMessage = defaultMessage;
  }

  @Override
  public String code() {
    return "DOCUMENT-" + name();
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
