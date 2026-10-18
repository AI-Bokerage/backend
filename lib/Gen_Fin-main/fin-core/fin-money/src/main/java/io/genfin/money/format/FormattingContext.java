package io.genfin.money.format;

import java.util.Locale;

public record FormattingContext(Locale locale, FormatStyle style) {

  public static FormattingContext of(Locale locale, FormatStyle style) {
    return new FormattingContext(locale, style);
  }
}
