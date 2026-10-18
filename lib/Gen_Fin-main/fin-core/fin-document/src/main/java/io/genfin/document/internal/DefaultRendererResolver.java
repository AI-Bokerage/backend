package io.genfin.document.internal;

import io.genfin.document.api.identity.RendererId;
import io.genfin.document.port.DocumentRenderer;
import io.genfin.document.port.RendererResolver;

public final class DefaultRendererResolver implements RendererResolver {

  private final DefaultRendererRegistry registry;

  public DefaultRendererResolver(DefaultRendererRegistry registry) {
    this.registry = registry;
  }

  @Override
  public DocumentRenderer resolve(RendererId id) {
    return registry.get(id);
  }
}
