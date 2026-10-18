package io.genfin.document.api.watermark;

import io.genfin.api.validation.Validate;

/** Watermark aggregate combining label, placement, and opacity. */
public final class Watermark {

  private final WatermarkLabel label;
  private final WatermarkPlacement placement;
  private final WatermarkOpacity opacity;

  private Watermark(WatermarkLabel label, WatermarkPlacement placement, WatermarkOpacity opacity) {
    Validate.notNull(label, "label must not be null");
    Validate.notNull(placement, "placement must not be null");
    Validate.notNull(opacity, "opacity must not be null");
    this.label = label;
    this.placement = placement;
    this.opacity = opacity;
  }

  public static Watermark of(
      WatermarkLabel label, WatermarkPlacement placement, WatermarkOpacity opacity) {
    return new Watermark(label, placement, opacity);
  }

  public WatermarkLabel label() {
    return label;
  }

  public WatermarkPlacement placement() {
    return placement;
  }

  public WatermarkOpacity opacity() {
    return opacity;
  }
}
