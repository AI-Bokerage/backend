package io.genfin.document.port;

import io.genfin.document.api.compose.ComposedDocument;
import io.genfin.document.api.identity.RendererId;
import io.genfin.document.api.validation.ValidationResult;

/** Pre-flight validation of a {@link ComposedDocument} and rendering options before rendering. */
public interface DocumentValidator {

  ValidationResult validate(
      ComposedDocument document, RendererConfiguration configuration, RendererId targetRenderer);
}
