package io.genfin.document.api.layout;

import io.genfin.api.validation.Validate;

public record StandardOrientation(String code) implements Orientation {

  public StandardOrientation {
    Validate.notBlank(code, "Orientation code must not be blank");
  }

  public static StandardOrientation of(String code) {
    return new StandardOrientation(code);
  }

  public static final StandardOrientation PORTRAIT = of("PORTRAIT");
  public static final StandardOrientation LANDSCAPE = of("LANDSCAPE");
}
