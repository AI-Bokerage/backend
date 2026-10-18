package io.genfin.document.api.layout;

import io.genfin.api.validation.Validate;

public record StandardPaperSize(String code) implements PaperSize {

  public StandardPaperSize {
    Validate.notBlank(code, "PaperSize code must not be blank");
  }

  public static StandardPaperSize of(String code) {
    return new StandardPaperSize(code);
  }

  public static final StandardPaperSize A4 = of("A4");
  public static final StandardPaperSize LETTER = of("LETTER");
}
