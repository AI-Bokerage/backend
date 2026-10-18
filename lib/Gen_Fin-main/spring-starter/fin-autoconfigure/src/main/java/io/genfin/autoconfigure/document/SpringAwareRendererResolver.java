package io.genfin.autoconfigure.document;

import io.genfin.document.api.identity.RendererId;
import io.genfin.document.port.DocumentRenderer;
import io.genfin.document.port.RendererResolver;
import java.util.Map;

/**
 * Spring-layer-only convenience: {@code fin-document}'s {@code DocumentRenderers.standard()} has no
 * public way to add a renderer beyond its 6 built-ins (confirmed: the backing registry/resolver are
 * internal, not exported). This resolver checks Spring-discovered {@link DocumentRenderer} beans by
 * id first, falling back to the standard resolver — it does not make fin-document itself
 * extensible, it only lets a Spring application register an additional renderer bean.
 */
final class SpringAwareRendererResolver implements RendererResolver {

  private final Map<RendererId, DocumentRenderer> customRenderers;
  private final RendererResolver delegate;

  SpringAwareRendererResolver(
      Map<RendererId, DocumentRenderer> customRenderers, RendererResolver delegate) {
    this.customRenderers = Map.copyOf(customRenderers);
    this.delegate = delegate;
  }

  @Override
  public DocumentRenderer resolve(RendererId id) {
    DocumentRenderer custom = customRenderers.get(id);
    if (custom != null) {
      return custom;
    }
    return delegate.resolve(id);
  }
}
