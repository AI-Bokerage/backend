package io.genfin.reconciliation.discrepancy;

/** The built-in {@link DiscrepancyCategory} implementations, one per {@code DifferenceType}. */
public enum StandardDiscrepancyCategory implements DiscrepancyCategory {
  AMOUNT_MISMATCH,
  CURRENCY_MISMATCH,
  REFERENCE_MISMATCH,
  STATUS_MISMATCH,
  TIMING_MISMATCH,
  METADATA_MISMATCH,
  ATTRIBUTE_MISMATCH,
  UNMATCHED_RECORD;

  @Override
  public String code() {
    return name();
  }
}
