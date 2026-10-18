package io.genfin.invoice.numbering;

import io.genfin.api.validation.Validate;

/**
 * A numbering pattern such as {@code "INV-{YEAR}-{SEQ}"}. The engine never interprets tokens itself
 * — a {@code NumberFormatter} does.
 */
public record NumberTemplate(String pattern) {

  public NumberTemplate {
    Validate.notBlank(pattern, "pattern must not be blank.");
  }

  public static NumberTemplate of(String pattern) {
    return new NumberTemplate(pattern);
  }
}
