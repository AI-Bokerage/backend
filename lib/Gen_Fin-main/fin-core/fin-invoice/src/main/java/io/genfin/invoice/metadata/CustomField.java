package io.genfin.invoice.metadata;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;

/** One named {@link TypedValue} entry within {@link Metadata}. */
public record CustomField(String name, TypedValue value) implements ValueObject {

  public CustomField {
    Validate.notBlank(name, "name must not be blank.");
    Validate.notNull(value, "value must not be null.");
  }
}
