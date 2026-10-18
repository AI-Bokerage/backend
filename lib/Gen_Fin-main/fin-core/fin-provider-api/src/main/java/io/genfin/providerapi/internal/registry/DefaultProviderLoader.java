package io.genfin.providerapi.internal.registry;

import io.genfin.providerapi.config.ProviderConfiguration;
import io.genfin.providerapi.port.registry.ProviderFactory;
import io.genfin.providerapi.port.registry.ProviderLoader;
import io.genfin.providerapi.port.registry.ProviderRegistry;
import java.util.List;

public final class DefaultProviderLoader implements ProviderLoader {

  @Override
  public void load(
      List<ProviderConfiguration> configurations,
      ProviderRegistry registry,
      ProviderFactory factory) {
    for (ProviderConfiguration configuration : configurations) {
      registry.register(configuration.descriptor(), factory.create(configuration));
    }
  }
}
