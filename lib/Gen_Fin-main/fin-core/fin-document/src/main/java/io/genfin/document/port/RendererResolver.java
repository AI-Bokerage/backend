package io.genfin.document.port;

import io.genfin.document.api.identity.RendererId;

/** Looks up a registered {@link DocumentRenderer} by its {@link RendererId}. */
public interface RendererResolver {
  DocumentRenderer resolve(RendererId id);
}
