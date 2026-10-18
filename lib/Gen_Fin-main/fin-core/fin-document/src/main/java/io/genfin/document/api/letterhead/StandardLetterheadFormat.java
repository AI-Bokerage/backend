package io.genfin.document.api.letterhead;

import io.genfin.api.validation.Validate;

public record StandardLetterheadFormat(String code) implements LetterheadFormat {

  public StandardLetterheadFormat {
    Validate.notBlank(code, "LetterheadFormat code must not be blank");
  }

  public static StandardLetterheadFormat of(String code) {
    return new StandardLetterheadFormat(code);
  }

  public static final StandardLetterheadFormat PDF = of("PDF");
  public static final StandardLetterheadFormat PNG = of("PNG");
  public static final StandardLetterheadFormat JPEG = of("JPEG");
  public static final StandardLetterheadFormat SVG = of("SVG");
  public static final StandardLetterheadFormat BLANK = of("BLANK");
}
