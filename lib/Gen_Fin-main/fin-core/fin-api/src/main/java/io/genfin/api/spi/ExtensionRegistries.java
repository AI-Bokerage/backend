package io.genfin.api.spi;

import io.genfin.api.internal.spi.DefaultExtensionRegistry;

/** Factory for {@link ExtensionRegistry} instances. Consumers must obtain registries here. */
public final class ExtensionRegistries {

  private ExtensionRegistries() {}

  public static ExtensionRegistry create() {
    return new DefaultExtensionRegistry();
  }
}
