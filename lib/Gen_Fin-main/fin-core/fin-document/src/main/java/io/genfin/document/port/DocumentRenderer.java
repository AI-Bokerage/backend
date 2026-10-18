package io.genfin.document.port;

import io.genfin.api.port.spi.Extension;
import io.genfin.document.api.compose.ComposedDocument;
import io.genfin.document.api.exception.RenderException;
import io.genfin.document.api.identity.RendererId;
import io.genfin.document.api.result.RenderResult;

/** Renders a {@link ComposedDocument} into a specific output format (e.g. JSON, Markdown, CSV). */
public interface DocumentRenderer extends Extension {

  RendererId id();

  RendererCapabilities capabilities();

  RenderResult render(ComposedDocument document, RendererConfiguration configuration)
      throws RenderException;
}
