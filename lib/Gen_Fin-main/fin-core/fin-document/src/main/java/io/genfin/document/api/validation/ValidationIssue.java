package io.genfin.document.api.validation;

import io.genfin.api.validation.Validate;

/** A single problem found by a {@link io.genfin.document.port.DocumentValidator}. */
public final class ValidationIssue {

  private final String code;
  private final String message;

  private ValidationIssue(String code, String message) {
    Validate.notBlank(code, "code must not be blank");
    Validate.notBlank(message, "message must not be blank");
    this.code = code;
    this.message = message;
  }

  public static ValidationIssue of(String code, String message) {
    return new ValidationIssue(code, message);
  }

  public String code() {
    return code;
  }

  public String message() {
    return message;
  }
}
