package io.genfin.dunning.reference;

import io.genfin.api.validation.Validate;

/**
 * A small convenience set of common reference kinds. Not exhaustive - fin-dunning is deliberately
 * receivable-centric, not invoice/subscription-centric, so applications are free to {@link
 * #of(String)} any code an invoice, subscription renewal, loan installment, EMI, or vendor payment
 * needs.
 */
public record StandardReferenceType(String code) implements ReferenceType {

  public StandardReferenceType {
    Validate.notBlank(code, "code must not be blank.");
  }

  public static StandardReferenceType of(String code) {
    return new StandardReferenceType(code);
  }

  /** The consuming application's own record backing the obligation (invoice, loan, ...). */
  public static final StandardReferenceType OBLIGATION_SOURCE = of("OBLIGATION_SOURCE");

  /** The party the obligation is owed by (customer, tenant, borrower, ...). */
  public static final StandardReferenceType OBLIGOR = of("OBLIGOR");
}
