package io.genfin.document.api.letterhead;

import io.genfin.api.validation.Validate;

public record StandardLetterheadOverlayPolicy(String code) implements LetterheadOverlayPolicy {

  public StandardLetterheadOverlayPolicy {
    Validate.notBlank(code, "LetterheadOverlayPolicy code must not be blank");
  }

  public static StandardLetterheadOverlayPolicy of(String code) {
    return new StandardLetterheadOverlayPolicy(code);
  }

  public static final StandardLetterheadOverlayPolicy CONTENT_ON_TOP = of("CONTENT_ON_TOP");
  public static final StandardLetterheadOverlayPolicy LETTERHEAD_ON_TOP = of("LETTERHEAD_ON_TOP");
}
