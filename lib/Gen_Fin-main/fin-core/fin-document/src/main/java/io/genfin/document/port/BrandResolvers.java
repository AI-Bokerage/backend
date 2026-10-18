package io.genfin.document.port;

import io.genfin.document.api.brand.BrandProfile;
import io.genfin.document.internal.brand.DefaultBrandRegistry;
import io.genfin.document.internal.brand.DefaultBrandResolver;

/**
 * Factory for a standard, ready-to-use {@link BrandResolver} plus a handle to register {@link
 * BrandProfile}s against it. This is the only consumer-reachable way to obtain a working {@link
 * BrandResolver}: the concrete registry/resolver implementations all live in internal packages that
 * the module never exports.
 */
public final class BrandResolvers {

  private BrandResolvers() {}

  public static Bundle standard() {
    DefaultBrandRegistry registry = new DefaultBrandRegistry();
    BrandResolver resolver = new DefaultBrandResolver(registry);
    return new Bundle() {
      @Override
      public void register(BrandProfile profile) {
        registry.register(profile);
      }

      @Override
      public BrandResolver resolver() {
        return resolver;
      }
    };
  }

  /** Registration handle and working resolver, wired together against the same backing registry. */
  public interface Bundle {
    void register(BrandProfile profile);

    BrandResolver resolver();
  }
}
