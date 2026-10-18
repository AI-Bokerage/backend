package io.genfin.document.port;

import io.genfin.api.port.spi.Extension;
import io.genfin.document.api.placeholder.PlaceholderValue;

public interface PlaceholderProvider extends Extension {
  boolean supports(String key);

  PlaceholderValue resolve(String key);
}
