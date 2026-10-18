package io.genfin.money.format;

import io.genfin.money.internal.format.DefaultMoneyFormatter;
import io.genfin.money.internal.format.DefaultMoneyParser;
import io.genfin.money.internal.format.SystemLocaleResolver;
import io.genfin.money.port.format.LocaleResolver;
import io.genfin.money.port.format.MoneyFormatter;
import io.genfin.money.port.format.MoneyParser;

/**
 * Factory for {@link MoneyFormatter}, {@link MoneyParser}, and {@link LocaleResolver} instances.
 */
public final class MoneyFormatters {

  private static final MoneyFormatter FORMATTER = new DefaultMoneyFormatter();
  private static final MoneyParser PARSER = new DefaultMoneyParser();
  private static final LocaleResolver SYSTEM_LOCALE = new SystemLocaleResolver();

  private MoneyFormatters() {}

  public static MoneyFormatter standard() {
    return FORMATTER;
  }

  public static MoneyParser standardParser() {
    return PARSER;
  }

  public static LocaleResolver systemLocale() {
    return SYSTEM_LOCALE;
  }
}
