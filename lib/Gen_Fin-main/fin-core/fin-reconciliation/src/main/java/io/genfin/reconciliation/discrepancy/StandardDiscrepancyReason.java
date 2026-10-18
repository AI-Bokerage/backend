package io.genfin.reconciliation.discrepancy;

/** The built-in {@link DiscrepancyReason} implementations. */
public enum StandardDiscrepancyReason implements DiscrepancyReason {
  AMOUNT_OUT_OF_TOLERANCE,
  CURRENCY_MISMATCH,
  REFERENCE_MISMATCH,
  STATUS_MISMATCH,
  TIMESTAMP_DRIFT,
  METADATA_MISMATCH,
  ATTRIBUTE_MISMATCH,
  MISSING_COUNTERPART,
  UNKNOWN;

  @Override
  public String code() {
    return name();
  }
}
