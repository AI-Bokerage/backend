package io.genfin.document.api.watermark;

import io.genfin.api.validation.Validate;

/** Watermark opacity value in the range [0.0, 1.0]. */
public final class WatermarkOpacity {

  public static final WatermarkOpacity DEFAULT = new WatermarkOpacity(0.3);

  private final double value;

  private WatermarkOpacity(double value) {
    Validate.required(
        value >= 0.0 && value <= 1.0, "opacity must be between 0.0 and 1.0 inclusive");
    this.value = value;
  }

  public static WatermarkOpacity of(double value) {
    return new WatermarkOpacity(value);
  }

  public double value() {
    return value;
  }
}
