package io.genfin.providerapi.port.registry;

import io.genfin.api.port.spi.Extension;
import io.genfin.providerapi.descriptor.ProviderDescriptor;
import java.util.List;
import java.util.Optional;

/**
 * A pure strategy: pick one descriptor among candidates (highest priority, round robin, cheapest,
 * ...).
 */
public interface ProviderSelector extends Extension {

  Optional<ProviderDescriptor> select(List<ProviderDescriptor> candidates);
}
