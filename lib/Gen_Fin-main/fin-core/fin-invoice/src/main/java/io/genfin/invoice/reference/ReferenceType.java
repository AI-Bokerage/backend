package io.genfin.invoice.reference;

/**
 * An application-defined classification of a {@link Reference} (e.g. "PROJECT", "SUBSCRIPTION",
 * "CUSTOMER").
 */
public interface ReferenceType {

  String code();
}
