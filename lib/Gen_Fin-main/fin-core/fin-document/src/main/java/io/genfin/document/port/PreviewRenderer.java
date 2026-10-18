package io.genfin.document.port;

import io.genfin.document.api.compose.ComposedDocument;
import io.genfin.document.api.exception.RenderException;
import io.genfin.document.api.preview.DocumentPreview;
import io.genfin.document.api.preview.PreviewConfiguration;

/**
 * Renders a preview of a {@link ComposedDocument} by delegating to a resolved {@link
 * DocumentRenderer}.
 */
public interface PreviewRenderer {

  DocumentPreview preview(
      ComposedDocument document,
      RendererConfiguration configuration,
      PreviewConfiguration previewConfiguration)
      throws RenderException;
}
