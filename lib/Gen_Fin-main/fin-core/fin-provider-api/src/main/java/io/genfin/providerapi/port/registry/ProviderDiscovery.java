package io.genfin.providerapi.port.registry;

import io.genfin.api.port.spi.Extension;
import io.genfin.providerapi.descriptor.ProviderDescriptor;
import java.util.List;

/**
 * Runtime discovery of what providers are available — typically backed by the {@code
 * ExtensionRegistry}.
 */
public interface ProviderDiscovery extends Extension {

  List<ProviderDescriptor> discover();
}
