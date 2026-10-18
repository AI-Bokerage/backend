package io.genfin.invoice.adjustment;

public enum StandardAdjustmentType implements AdjustmentType {
  CREDIT,
  DEBIT,
  CORRECTION,
  PENALTY,
  FEE,
  SURCHARGE,
  MANUAL;

  @Override
  public String code() {
    return name();
  }
}
