package io.genfin.document.port;

import io.genfin.document.api.placeholder.PlaceholderValue;

public interface PlaceholderResolver {
  PlaceholderValue resolve(String key);
}
