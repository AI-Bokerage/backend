package io.genfin.payment.reference;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;

/**
 * A link from a payment to an application-owned concept (invoice, customer, order, ...) via an
 * opaque type/value pair.
 */
public record Reference(ReferenceType type, ReferenceValue value) implements ValueObject {

  public Reference {
    Validate.notNull(type, "type must not be null.");
    Validate.notNull(value, "value must not be null.");
  }

  public static Reference of(ReferenceType type, String value) {
    return new Reference(type, new ReferenceValue(value));
  }

  public static Reference invoice(String invoiceId) {
    return of(StandardReferenceType.INVOICE, invoiceId);
  }

  public static Reference customer(String customerId) {
    return of(StandardReferenceType.CUSTOMER, customerId);
  }
}
