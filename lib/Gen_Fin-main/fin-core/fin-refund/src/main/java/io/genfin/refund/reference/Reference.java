package io.genfin.refund.reference;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;

/**
 * A link from a refund to an application-owned concept (invoice, customer, ...) via an opaque
 * type/value pair. Deliberately fin-refund's own copy — never fin-payment's {@code Reference}.
 */
public record Reference(ReferenceType type, ReferenceValue value) implements ValueObject {

  public Reference {
    Validate.notNull(type, "type must not be null.");
    Validate.notNull(value, "value must not be null.");
  }

  public static Reference of(ReferenceType type, String value) {
    return new Reference(type, new ReferenceValue(value));
  }

  public static Reference payment(String paymentId) {
    return of(StandardReferenceType.PAYMENT, paymentId);
  }

  public static Reference invoice(String invoiceId) {
    return of(StandardReferenceType.INVOICE, invoiceId);
  }

  public static Reference customer(String customerId) {
    return of(StandardReferenceType.CUSTOMER, customerId);
  }
}
