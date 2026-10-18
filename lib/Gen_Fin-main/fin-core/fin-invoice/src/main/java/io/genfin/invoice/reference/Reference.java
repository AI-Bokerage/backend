package io.genfin.invoice.reference;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import java.util.Optional;

/**
 * One link from an invoice (or line) to an application-owned concept, via an opaque type/value
 * pair.
 */
public record Reference(ReferenceType type, ReferenceValue value, String displayLabel)
    implements ValueObject {

  public Reference {
    Validate.notNull(type, "type must not be null.");
    Validate.notNull(value, "value must not be null.");
  }

  public static Reference of(ReferenceType type, String value) {
    return new Reference(type, new ReferenceValue(value), null);
  }

  public static Reference of(ReferenceType type, String value, String displayLabel) {
    return new Reference(type, new ReferenceValue(value), displayLabel);
  }

  public Optional<String> label() {
    return Optional.ofNullable(displayLabel);
  }
}
