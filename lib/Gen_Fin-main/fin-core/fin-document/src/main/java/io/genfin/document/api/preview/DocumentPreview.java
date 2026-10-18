package io.genfin.document.api.preview;

import io.genfin.api.validation.Validate;

/**
 * Public-facing wrapper around a {@link PreviewResult}, returned by a {@link
 * io.genfin.document.port.PreviewRenderer}.
 */
public final class DocumentPreview {

  private final PreviewResult result;

  private DocumentPreview(PreviewResult result) {
    Validate.notNull(result, "result must not be null");
    this.result = result;
  }

  public static DocumentPreview of(PreviewResult result) {
    return new DocumentPreview(result);
  }

  public PreviewResult result() {
    return result;
  }
}
