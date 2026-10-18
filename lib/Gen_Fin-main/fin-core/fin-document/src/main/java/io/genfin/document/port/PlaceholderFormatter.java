package io.genfin.document.port;

import io.genfin.api.port.spi.Extension;

public interface PlaceholderFormatter extends Extension {
  String format(String rawValue);
}
