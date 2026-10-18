package io.genfin.invoice.factory;

import io.genfin.invoice.reference.Reference;
import io.genfin.invoice.reference.ReferenceType;
import io.genfin.invoice.reference.StandardReferenceType;

public final class ReferenceFactory {

  private ReferenceFactory() {}

  public static Reference of(String typeCode, String value) {
    return Reference.of(StandardReferenceType.of(typeCode), value);
  }

  public static Reference of(ReferenceType type, String value, String displayLabel) {
    return Reference.of(type, value, displayLabel);
  }
}
