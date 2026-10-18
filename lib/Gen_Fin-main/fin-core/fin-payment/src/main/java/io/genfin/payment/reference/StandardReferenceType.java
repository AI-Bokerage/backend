package io.genfin.payment.reference;

import io.genfin.api.validation.Validate;

public record StandardReferenceType(String code) implements ReferenceType {

  public StandardReferenceType {
    Validate.notBlank(code, "code must not be blank.");
  }

  public static StandardReferenceType of(String code) {
    return new StandardReferenceType(code);
  }

  public static final StandardReferenceType INVOICE = of("INVOICE");
  public static final StandardReferenceType CUSTOMER = of("CUSTOMER");
  public static final StandardReferenceType ORDER = of("ORDER");
}
