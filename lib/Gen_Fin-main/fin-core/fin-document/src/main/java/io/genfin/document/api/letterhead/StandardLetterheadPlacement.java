package io.genfin.document.api.letterhead;

import io.genfin.api.validation.Validate;

public record StandardLetterheadPlacement(String code) implements LetterheadPlacement {

  public StandardLetterheadPlacement {
    Validate.notBlank(code, "LetterheadPlacement code must not be blank");
  }

  public static StandardLetterheadPlacement of(String code) {
    return new StandardLetterheadPlacement(code);
  }

  public static final StandardLetterheadPlacement FULL_PAGE = of("FULL_PAGE");
}
