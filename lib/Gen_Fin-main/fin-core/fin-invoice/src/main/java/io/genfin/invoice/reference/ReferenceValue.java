package io.genfin.invoice.reference;

import io.genfin.api.validation.Validate;

/**
 * The opaque identifying value of a {@link Reference} — the engine never interprets its contents.
 */
public record ReferenceValue(String value) {

  public ReferenceValue {
    Validate.notBlank(value, "value must not be blank.");
  }
}
