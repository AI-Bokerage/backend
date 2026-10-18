package io.genfin.reconciliation.comparison;

/** Which dimension of a {@link ComparisonRecord} a {@link Difference} was found on. */
public enum DifferenceType {
  AMOUNT,
  CURRENCY,
  REFERENCE,
  STATUS,
  TIMESTAMP,
  METADATA,
  ATTRIBUTE
}
