package io.genfin.document.internal;

import io.genfin.document.api.exception.RendererNotFoundException;
import io.genfin.document.api.identity.RendererId;
import io.genfin.document.port.DocumentRenderer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultRendererRegistry {

  private final Map<RendererId, DocumentRenderer> renderers = new ConcurrentHashMap<>();

  public void register(DocumentRenderer renderer) {
    DocumentRenderer existing = renderers.putIfAbsent(renderer.id(), renderer);
    if (existing != null) {
      throw new IllegalStateException("Renderer already registered for id: " + renderer.id());
    }
  }

  public DocumentRenderer get(RendererId id) {
    DocumentRenderer renderer = renderers.get(id);
    if (renderer == null) {
      throw new RendererNotFoundException(id);
    }
    return renderer;
  }
}
