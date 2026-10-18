package io.genfin.providerapi.port.registry;

import io.genfin.api.port.spi.Extension;
import io.genfin.providerapi.config.ProviderConfiguration;
import java.util.List;

/**
 * Config-driven activation: given a set of provider configurations, load and register the
 * corresponding gateways.
 */
public interface ProviderLoader extends Extension {

  void load(
      List<ProviderConfiguration> configurations,
      ProviderRegistry registry,
      ProviderFactory factory);
}
