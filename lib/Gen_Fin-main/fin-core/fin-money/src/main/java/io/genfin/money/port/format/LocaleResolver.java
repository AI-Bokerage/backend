package io.genfin.money.port.format;

import io.genfin.api.port.spi.Extension;
import java.util.Locale;

public interface LocaleResolver extends Extension {

  Locale resolve();
}
