package io.genfin.document.internal.preview;

import io.genfin.api.validation.Validate;
import io.genfin.document.api.compose.ComposedDocument;
import io.genfin.document.api.exception.RenderException;
import io.genfin.document.api.preview.DocumentPreview;
import io.genfin.document.api.preview.PreviewConfiguration;
import io.genfin.document.api.preview.PreviewResult;
import io.genfin.document.api.result.RenderResult;
import io.genfin.document.port.PreviewRenderer;
import io.genfin.document.port.RendererConfiguration;
import io.genfin.document.port.RendererResolver;
import java.util.Optional;

/**
 * Default {@link PreviewRenderer}, delegating to a renderer resolved via an injected {@link
 * RendererResolver}.
 */
public final class DefaultPreviewRenderer implements PreviewRenderer {

  private final RendererResolver resolver;

  public DefaultPreviewRenderer(RendererResolver resolver) {
    Validate.notNull(resolver, "resolver must not be null");
    this.resolver = resolver;
  }

  @Override
  public DocumentPreview preview(
      ComposedDocument document,
      RendererConfiguration configuration,
      PreviewConfiguration previewConfiguration)
      throws RenderException {
    Validate.notNull(document, "document must not be null");
    Validate.notNull(configuration, "configuration must not be null");
    Validate.notNull(previewConfiguration, "previewConfiguration must not be null");

    RenderResult renderResult =
        resolver.resolve(previewConfiguration.targetRenderer()).render(document, configuration);
    return DocumentPreview.of(PreviewResult.of(renderResult, Optional.empty()));
  }
}
