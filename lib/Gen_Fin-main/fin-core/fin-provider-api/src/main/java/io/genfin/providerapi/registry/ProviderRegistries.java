package io.genfin.providerapi.registry;

import io.genfin.providerapi.internal.registry.DefaultProviderLoader;
import io.genfin.providerapi.internal.registry.DefaultProviderRegistry;
import io.genfin.providerapi.internal.registry.DefaultProviderResolver;
import io.genfin.providerapi.internal.registry.HighestPriorityProviderSelector;
import io.genfin.providerapi.port.registry.ProviderLoader;
import io.genfin.providerapi.port.registry.ProviderRegistry;
import io.genfin.providerapi.port.registry.ProviderResolver;
import io.genfin.providerapi.port.registry.ProviderSelector;

public final class ProviderRegistries {

  private static final ProviderSelector HIGHEST_PRIORITY = new HighestPriorityProviderSelector();
  private static final ProviderLoader STANDARD_LOADER = new DefaultProviderLoader();

  private ProviderRegistries() {}

  public static ProviderRegistry empty() {
    return new DefaultProviderRegistry();
  }

  public static ProviderSelector highestPriority() {
    return HIGHEST_PRIORITY;
  }

  public static ProviderResolver standardResolver() {
    return new DefaultProviderResolver(HIGHEST_PRIORITY);
  }

  public static ProviderLoader standardLoader() {
    return STANDARD_LOADER;
  }
}
