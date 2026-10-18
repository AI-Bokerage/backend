package io.genfin.document.api.watermark;

import io.genfin.api.validation.Validate;

/** A watermark placement identified by a simple code string (e.g. {@code "DIAGONAL_CENTER"}). */
public record StandardWatermarkPlacement(String code) implements WatermarkPlacement {

  public StandardWatermarkPlacement {
    Validate.notBlank(code, "WatermarkPlacement code must not be blank");
  }

  public static StandardWatermarkPlacement of(String code) {
    return new StandardWatermarkPlacement(code);
  }

  public static final StandardWatermarkPlacement DIAGONAL_CENTER = of("DIAGONAL_CENTER");
}
