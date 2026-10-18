package io.genfin.reconciliation.tolerance;

/** The built-in {@link ToleranceType} implementations, one per {@link Tolerance} kind. */
public enum StandardToleranceType implements ToleranceType {
  AMOUNT,
  DATE,
  PERCENTAGE,
  CURRENCY,
  CUSTOM;

  @Override
  public String code() {
    return name();
  }
}
