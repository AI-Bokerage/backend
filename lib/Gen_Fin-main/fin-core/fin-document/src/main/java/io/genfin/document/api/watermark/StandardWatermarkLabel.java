package io.genfin.document.api.watermark;

import io.genfin.api.validation.Validate;

/** A watermark label identified by a simple code string (e.g. {@code "DRAFT"}). */
public record StandardWatermarkLabel(String code) implements WatermarkLabel {

  public StandardWatermarkLabel {
    Validate.notBlank(code, "WatermarkLabel code must not be blank");
  }

  public static StandardWatermarkLabel of(String code) {
    return new StandardWatermarkLabel(code);
  }

  public static final StandardWatermarkLabel DRAFT = of("DRAFT");
  public static final StandardWatermarkLabel PAID = of("PAID");
  public static final StandardWatermarkLabel VOID = of("VOID");
  public static final StandardWatermarkLabel COPY = of("COPY");
  public static final StandardWatermarkLabel CONFIDENTIAL = of("CONFIDENTIAL");
}
