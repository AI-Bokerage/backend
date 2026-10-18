package io.genfin.money.internal.format;

import io.genfin.money.port.format.LocaleResolver;
import java.util.Locale;

public final class SystemLocaleResolver implements LocaleResolver {

  @Override
  public Locale resolve() {
    return Locale.getDefault();
  }
}
