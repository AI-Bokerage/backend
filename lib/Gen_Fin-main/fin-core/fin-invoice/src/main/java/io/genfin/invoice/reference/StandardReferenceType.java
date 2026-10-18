package io.genfin.invoice.reference;

import io.genfin.api.validation.Validate;

/**
 * An ad-hoc {@link ReferenceType} identified only by its code — the common case for
 * application-defined types.
 */
public record StandardReferenceType(String code) implements ReferenceType {

  public StandardReferenceType {
    Validate.notBlank(code, "code must not be blank.");
  }

  public static StandardReferenceType of(String code) {
    return new StandardReferenceType(code);
  }
}
