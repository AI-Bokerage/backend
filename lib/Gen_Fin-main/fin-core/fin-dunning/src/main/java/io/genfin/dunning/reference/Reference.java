package io.genfin.dunning.reference;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;

/**
 * A link from a {@code FinancialObligation} back to whatever the consuming application's real
 * domain object is (an invoice, a subscription renewal, a loan installment, a marketplace
 * settlement, ...) via an opaque type/value pair. Deliberately fin-dunning's own copy - never
 * fin-refund's or fin-reconciliation's {@code Reference} - so fin-dunning stays isolated from those
 * modules.
 */
public record Reference(ReferenceType type, ReferenceValue value) implements ValueObject {

  public Reference {
    Validate.notNull(type, "type must not be null.");
    Validate.notNull(value, "value must not be null.");
  }

  public static Reference of(ReferenceType type, String value) {
    return new Reference(type, new ReferenceValue(value));
  }

  public static Reference obligationSource(String id) {
    return of(StandardReferenceType.OBLIGATION_SOURCE, id);
  }

  public static Reference obligor(String id) {
    return of(StandardReferenceType.OBLIGOR, id);
  }
}
