package io.genfin.document.internal.brand;

import io.genfin.document.api.brand.BrandProfile;
import io.genfin.document.api.identity.BrandId;
import io.genfin.document.port.BrandResolver;

public final class DefaultBrandResolver implements BrandResolver {

  private final DefaultBrandRegistry registry;

  public DefaultBrandResolver(DefaultBrandRegistry registry) {
    this.registry = registry;
  }

  @Override
  public BrandProfile resolve(BrandId id) {
    return registry.get(id);
  }
}
