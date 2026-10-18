package io.genfin.dunning.reference;

/**
 * The kind of application-owned concept a {@link Reference} points at. Not a closed enum -
 * implement this to add whatever reference kinds a deployment needs (invoice, subscription renewal,
 * loan installment, vendor bill, ...), the same extensible-taxonomy pattern used throughout Gen-Fin
 * (e.g. {@code io.genfin.refund.reference.ReferenceType}).
 */
public interface ReferenceType {

  String code();
}
