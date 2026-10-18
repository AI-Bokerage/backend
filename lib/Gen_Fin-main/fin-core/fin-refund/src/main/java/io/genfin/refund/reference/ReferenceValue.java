package io.genfin.refund.reference;

import io.genfin.api.validation.Validate;

public record ReferenceValue(String value) {

  public ReferenceValue {
    Validate.notBlank(value, "value must not be blank.");
  }
}
