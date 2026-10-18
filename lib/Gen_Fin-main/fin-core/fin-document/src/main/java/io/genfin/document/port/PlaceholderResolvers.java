package io.genfin.document.port;

import io.genfin.document.internal.placeholder.DefaultPlaceholderRegistry;
import io.genfin.document.internal.placeholder.DefaultPlaceholderResolver;

/**
 * Factory for a standard, ready-to-use {@link PlaceholderResolver} plus a handle to register {@link
 * PlaceholderProvider}s against it. Closes the gap left by {@link TemplateEngines}: a {@link
 * io.genfin.document.api.template.TemplateContext} needs a {@link PlaceholderResolver} to be
 * constructed, and this is the only consumer-reachable way to build one.
 */
public final class PlaceholderResolvers {

  private PlaceholderResolvers() {}

  public static Bundle standard() {
    DefaultPlaceholderRegistry registry = new DefaultPlaceholderRegistry();
    PlaceholderResolver resolver = new DefaultPlaceholderResolver(registry);
    return new Bundle() {
      @Override
      public void register(PlaceholderProvider provider) {
        registry.register(provider);
      }

      @Override
      public PlaceholderResolver resolver() {
        return resolver;
      }
    };
  }

  /** Registration handle and working resolver, wired together against the same backing registry. */
  public interface Bundle {
    void register(PlaceholderProvider provider);

    PlaceholderResolver resolver();
  }
}
