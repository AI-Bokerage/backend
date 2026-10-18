package io.genfin.document.port;

import io.genfin.document.internal.preview.DefaultPreviewRenderer;

/**
 * Factory for a standard, ready-to-use {@link PreviewRenderer}. This is the only consumer-reachable
 * way to obtain a working {@link PreviewRenderer}: the concrete implementation lives in an internal
 * package that the module never exports.
 */
public final class PreviewRenderers {

  private PreviewRenderers() {}

  public static PreviewRenderer of(RendererResolver resolver) {
    return new DefaultPreviewRenderer(resolver);
  }
}
