package io.genfin.invoice.discount;

public enum StandardDiscountType implements DiscountType {
  FIXED,
  PERCENTAGE,
  TIER,
  VOLUME,
  COUPON,
  PROMOTIONAL,
  MANUAL;

  @Override
  public String code() {
    return name();
  }
}
