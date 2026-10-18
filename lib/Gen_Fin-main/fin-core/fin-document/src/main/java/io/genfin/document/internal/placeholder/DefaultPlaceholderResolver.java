package io.genfin.document.internal.placeholder;

import io.genfin.document.api.placeholder.MissingPlaceholderValue;
import io.genfin.document.api.placeholder.PlaceholderValue;
import io.genfin.document.port.PlaceholderProvider;
import io.genfin.document.port.PlaceholderResolver;

public final class DefaultPlaceholderResolver implements PlaceholderResolver {

  private final DefaultPlaceholderRegistry registry;

  public DefaultPlaceholderResolver(DefaultPlaceholderRegistry registry) {
    this.registry = registry;
  }

  @Override
  public PlaceholderValue resolve(String key) {
    for (PlaceholderProvider provider : registry.providers()) {
      if (provider.supports(key)) {
        return provider.resolve(key);
      }
    }
    return MissingPlaceholderValue.instance();
  }
}
